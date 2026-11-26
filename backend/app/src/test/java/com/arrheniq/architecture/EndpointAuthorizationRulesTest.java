package com.arrheniq.architecture;

import com.arrheniq.kernel.security.AuthenticatedOnly;
import com.arrheniq.kernel.security.PublicEndpoint;
import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.kernel.security.SystemOperation;
import com.arrheniq.testsupport.Requirement;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import fixtures.endpointrules.GuardedController;
import fixtures.endpointrules.GuardedService;
import fixtures.endpointrules.SystemTaskService;
import fixtures.endpointrules.UnguardedController;
import fixtures.endpointrules.UnguardedService;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deny by default, checked at build time (URS-SEC-001, URS-IAM-005; security-architecture.md §4):
 * <ol>
 *   <li>every web endpoint is guarded: it declares {@link RequirePermission}, {@link AuthenticatedOnly} or
 *       {@link PublicEndpoint}, or it calls a service method that declares {@link RequirePermission};</li>
 *   <li>every public method of a domain service ({@code @Service} outside the kernel) declares
 *       {@link RequirePermission}, {@link AuthenticatedOnly} or {@link SystemOperation};</li>
 *   <li>no endpoint calls a {@link SystemOperation} (system work is never reachable from the API).</li>
 * </ol>
 * The rules are also run against small fixtures so a broken rule cannot pass silently.
 * {@code EndpointSweepIT} complements this at run time: every endpoint answers 401 to an anonymous caller.
 */
@Requirement({"URS-SEC-001", "URS-IAM-005"})
class EndpointAuthorizationRulesTest {

    private static final JavaClasses APP = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.arrheniq");

    // ------------------------------------------------------------------ rules

    static final ArchRule ENDPOINTS_ARE_GUARDED = methods()
            .that().areDeclaredInClassesThat().areMetaAnnotatedWith(Controller.class)
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should(beGuarded())
            .because("every endpoint must require a permission, a signed-in user, or be explicitly public");

    static final ArchRule SERVICE_METHODS_DECLARE_ACCESS = methods()
            .that().areDeclaredInClassesThat().areMetaAnnotatedWith(Service.class)
            .and().areDeclaredInClassesThat().resideOutsideOfPackages("com.arrheniq.kernel..", "com.arrheniq.devsupport..")
            .and().arePublic()
            .and().areNotStatic()
            .should(declareAccess())
            .because("domain services are where permissions are enforced (deny by default)")
            .allowEmptyShould(true);

    static final ArchRule ENDPOINTS_DO_NOT_CALL_SYSTEM_OPERATIONS = methods()
            .that().areDeclaredInClassesThat().areMetaAnnotatedWith(Controller.class)
            .should(notCallSystemOperations())
            .because("system work must never be reachable from the API");

    // ------------------------------------------------------------------ the application

    @Test
    void everyEndpointIsGuarded() {
        ENDPOINTS_ARE_GUARDED.check(APP);
    }

    @Test
    void everyDomainServiceMethodDeclaresItsAccess() {
        SERVICE_METHODS_DECLARE_ACCESS.check(APP);
    }

    @Test
    void noEndpointCallsASystemOperation() {
        ENDPOINTS_DO_NOT_CALL_SYSTEM_OPERATIONS.check(APP);
    }

    // ------------------------------------------------------------------ the rules themselves

    @Test
    void rulesAcceptGuardedCodeAndRejectUnguardedCode() {
        JavaClasses good = new ClassFileImporter().importClasses(GuardedController.class, GuardedService.class);
        ENDPOINTS_ARE_GUARDED.check(good);
        SERVICE_METHODS_DECLARE_ACCESS.check(good);
        ENDPOINTS_DO_NOT_CALL_SYSTEM_OPERATIONS.check(good);

        JavaClasses bad = new ClassFileImporter().importClasses(UnguardedController.class, UnguardedService.class,
                SystemTaskService.class);
        assertThrows(AssertionError.class, () -> ENDPOINTS_ARE_GUARDED.check(bad));
        assertThrows(AssertionError.class, () -> SERVICE_METHODS_DECLARE_ACCESS.check(bad));
        assertThrows(AssertionError.class, () -> ENDPOINTS_DO_NOT_CALL_SYSTEM_OPERATIONS.check(bad));
    }

    // ------------------------------------------------------------------ conditions

    @SafeVarargs
    private static boolean declaresAccess(JavaMethod m, Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> a : annotations) {
            if (m.isAnnotatedWith(a) || m.getOwner().isAnnotatedWith(a)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<JavaMethod> target(JavaMethodCall call) {
        return call.getTarget().resolveMember();
    }

    private static ArchCondition<JavaMethod> beGuarded() {
        return new ArchCondition<>("be guarded") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean guarded = declaresAccess(method, RequirePermission.class, AuthenticatedOnly.class,
                        PublicEndpoint.class)
                        || method.getMethodCallsFromSelf().stream()
                        .map(EndpointAuthorizationRulesTest::target)
                        .flatMap(Optional::stream)
                        .anyMatch(t -> declaresAccess(t, RequirePermission.class));
                if (!guarded) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " has no @RequirePermission/@AuthenticatedOnly/@PublicEndpoint and calls no"
                            + " permission-checked service method"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> declareAccess() {
        return new ArchCondition<>("declare @RequirePermission, @AuthenticatedOnly or @SystemOperation") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!declaresAccess(method, RequirePermission.class, AuthenticatedOnly.class, SystemOperation.class)) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " declares no access rule (@RequirePermission, @AuthenticatedOnly or @SystemOperation)"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallSystemOperations() {
        return new ArchCondition<>("not call @SystemOperation methods") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getMethodCallsFromSelf().stream()
                        .filter(call -> target(call).map(t -> declaresAccess(t, SystemOperation.class)).orElse(false))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                call.getDescription() + " (system operation called from a controller)")));
            }
        };
    }
}
