package com.arrheniq.devsupport;

import com.arrheniq.kernel.security.LocalUserContext;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import com.arrheniq.masterdata.ProductDetails;
import com.arrheniq.masterdata.ProductInput;
import com.arrheniq.masterdata.ProductService;
import com.arrheniq.masterdata.ProtocolDetails;
import com.arrheniq.masterdata.ProtocolInput;
import com.arrheniq.masterdata.ProtocolInput.TestInput;
import com.arrheniq.masterdata.ProtocolInput.TimePointInput;
import com.arrheniq.masterdata.ProtocolService;
import com.arrheniq.masterdata.ProtocolVersionDetails;
import com.arrheniq.masterdata.SpecTestInput;
import com.arrheniq.masterdata.SpecVersionDetails;
import com.arrheniq.masterdata.SpecificationDetails;
import com.arrheniq.masterdata.SpecificationService;
import com.arrheniq.masterdata.TestMethodInput;
import com.arrheniq.masterdata.TestMethodService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Demo data for the S1 walkthrough ({@code docs/delivery/s1-demo.md}): a test library, three products, two
 * specifications and two protocols in tenant A, one product in tenant B. Everything goes through the real,
 * audited application services as the demo users (smanager, bmanager), so the audit trail looks exactly as it
 * would for a person. Runs once, when tenant A has no product MET-500 yet; switch it off with
 * {@code arrheniq.dev.demo-data=false}. To start again, recreate the local database.
 *
 * <p>Nothing is approved here: approval is an electronic signature that needs a person re-entering their
 * password, so the walkthrough does it live. Records are left in DRAFT or IN_REVIEW for that.
 */
@Component
@Profile("local")
@Order(2)
class LocalDemoData implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDemoData.class);

    private final boolean enabled;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final LocalUserContext users;
    private final TestMethodService tests;
    private final ProductService products;
    private final SpecificationService specs;
    private final ProtocolService protocols;

    LocalDemoData(@Value("${arrheniq.dev.demo-data:true}") boolean enabled, JdbcClient jdbc,
                  PlatformTransactionManager txManager, LocalUserContext users, TestMethodService tests,
                  ProductService products, SpecificationService specs, ProtocolService protocols) {
        this.enabled = enabled;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.users = users;
        this.tests = tests;
        this.products = products;
        this.specs = specs;
        this.protocols = protocols;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || exists(LocalDevData.TENANT_A, "MET-500")) {
            return;
        }
        try {
            UUID nj = siteId(LocalDevData.TENANT_A, "NJ");
            users.runAs(LocalDevData.TENANT_A, "smanager", () -> tenantA(nj));
            UUID nc = siteId(LocalDevData.TENANT_B, "NC");
            users.runAs(LocalDevData.TENANT_B, "bmanager", () -> tenantB(nc));
            LOG.info("Demo data created (see docs/delivery/s1-demo.md)");
        } catch (RuntimeException e) {
            // never block local start-up; what was created stays (each step is its own audited transaction)
            LOG.warn("Demo data incomplete; recreate the local database to try again", e);
        }
    }

    // ------------------------------------------------------------------ tenant A

    private void tenantA(UUID site) {
        Map<String, UUID> lib = new HashMap<>();
        lib.put("ASSAY", numeric("ASSAY", "Assay (HPLC)", "TM-001 rev 4", "PCT_LC", 2, 1, "95", "105", "97", "103"));
        lib.put("IMP-B", numeric("IMP-B", "Impurity B (HPLC)", "TM-002 rev 2", "PCT", 1, 2, null, "0.2", null, "0.15"));
        lib.put("IMP-TOT", numeric("IMP-TOT", "Total impurities (HPLC)", "TM-002 rev 2", "PCT", 1, 2, null, "1.0", null,
                "0.8"));
        lib.put("DISS", numeric("DISS", "Dissolution, Q at 30 min", "TM-003 rev 1", "PCT", 6, 0, "80", null, "85", null));
        lib.put("KF", numeric("KF", "Water content (Karl Fischer)", "TM-004 rev 1", "PCT", 1, 1, null, "3.0", null, "2.5"));
        lib.put("PH", numeric("PH", "pH", "TM-005 rev 1", "PH", 1, 1, "3.0", "4.5", null, null));
        lib.put("APPEAR", text("APPEAR", "Appearance (visual)", "TM-010 rev 1", "Conforms to description"));

        // MET-500: the walkthrough product. Submitted, so QA approves it first.
        ProductDetails met = products.create(new ProductInput(site, "MET-500",
                "Metformin hydrochloride 500 mg film-coated tablets", "Immediate release; HDPE bottle of 60",
                "DRUG_PRODUCT", "FC_TABLET", "500 mg", "ESTABLISHED", 24, "MONTH", false));
        products.submit(met.product().id(), met.product().version());
        ProductDetails aml = products.create(new ProductInput(site, "AML-5", "Amlodipine besilate 5 mg tablets",
                "Development product, shelf life still to be set", "DRUG_PRODUCT", "TABLET", "5 mg",
                "TO_BE_DETERMINED", null, null, false));
        ProductDetails ond = products.create(new ProductInput(site, "OND-4", "Ondansetron 4 mg/5 mL oral solution",
                "Amber glass bottle of 50 mL", "DRUG_PRODUCT", "ORAL_SOLUTION", "4 mg/5 mL", "ESTABLISHED", 36,
                "MONTH", false));

        // SPEC-MET-500 v1: in review, approved live in the walkthrough
        SpecificationDetails metSpec = specs.create(met.product().id(), "SPEC-MET-500", "Shelf-life specification");
        SpecVersionDetails metV1 = specs.saveDraft(metSpec.versions().getFirst().id(), 0,
                "First issue, from the registration dossier", List.of(
                        limit(lib, "APPEAR", null, null, "White, oval, film-coated tablets, debossed “M 500”"),
                        limit(lib, "ASSAY", "95.0", "105.0", null),
                        limit(lib, "IMP-B", null, "0.20", null),
                        limit(lib, "IMP-TOT", null, "1.0", null),
                        limit(lib, "DISS", "80", null, null),
                        limit(lib, "KF", null, "3.0", null)));
        specs.submit(metV1.id(), metV1.version());

        // SPEC-OND-4 v1: a draft to edit
        SpecificationDetails ondSpec = specs.create(ond.product().id(), "SPEC-OND-4", "Shelf-life specification");
        specs.saveDraft(ondSpec.versions().getFirst().id(), 0, null, List.of(
                limit(lib, "APPEAR", null, null, "Clear, colourless to pale yellow solution"),
                limit(lib, "ASSAY", "95.0", "105.0", null),
                limit(lib, "PH", "3.0", "4.5", null),
                limit(lib, "IMP-TOT", null, "0.5", null)));
        specs.create(aml.product().id(), "SPEC-AML-5", "Development specification");

        // Long-term protocol from the ICH template and the specification: a draft, finished and submitted live
        ProtocolDetails lt = protocols.create(met.product().id(), "PRT-MET-500-LT",
                "Long-term 25 °C / 60 % RH, 36 months", "LT_ZONE_II_36M", metV1.id());
        // 0 3 6 9 12 18 24 36 months; dissolution and water at 0, 6, 12, 24, 36
        List<Integer> all = List.of(0, 1, 2, 3, 4, 5, 6, 7);
        List<Integer> yearly = List.of(0, 2, 4, 6, 7);
        protocols.saveDraft(lt.versions().getFirst().id(), 0, new ProtocolInput("LONG_TERM", "25C60RH", 4, "Bottle",
                "First issue", months(new int[] {0, 3, 6, 9, 12, 18, 24, 36}, new int[] {4, 2, 3, 2, 3, 2, 3, 3}),
                List.of(new TestInput(lib.get("APPEAR"), all), new TestInput(lib.get("ASSAY"), all),
                        new TestInput(lib.get("IMP-B"), all), new TestInput(lib.get("IMP-TOT"), all),
                        new TestInput(lib.get("DISS"), yearly), new TestInput(lib.get("KF"), yearly))));

        // Accelerated protocol: in review, for the segregation-of-duties and approval steps
        ProtocolDetails acc = protocols.create(met.product().id(), "PRT-MET-500-ACC",
                "Accelerated 40 °C / 75 % RH, 6 months", "ACC_40_75_6M", metV1.id());
        List<Integer> three = List.of(0, 1, 2);
        ProtocolVersionDetails accV1 = protocols.saveDraft(acc.versions().getFirst().id(), 0, new ProtocolInput(
                "ACCELERATED", "40C75RH", 2, "Bottle", "First issue",
                months(new int[] {0, 3, 6}, new int[] {3, 3, 3}),
                List.of(new TestInput(lib.get("APPEAR"), three), new TestInput(lib.get("ASSAY"), three),
                        new TestInput(lib.get("IMP-B"), three), new TestInput(lib.get("IMP-TOT"), three),
                        new TestInput(lib.get("DISS"), List.of(0, 2)), new TestInput(lib.get("KF"), three))));
        protocols.submit(accV1.id(), accV1.version());
    }

    // ------------------------------------------------------------------ tenant B (shows tenant isolation)

    private void tenantB(UUID site) {
        numeric("ASSAY", "Assay (UPLC)", "B-SOP-17", "PCT_LC", 2, 1, "90", "110", null, null);
        products.create(new ProductInput(site, "CIP-250", "Ciprofloxacin 250 mg tablets", null, "DRUG_PRODUCT",
                "FC_TABLET", "250 mg", "ESTABLISHED", 36, "MONTH", false));
    }

    // ------------------------------------------------------------------ helpers

    private UUID numeric(String code, String name, String ref, String unit, int replicates, int decimals,
                         @Nullable String lower, @Nullable String upper, @Nullable String alertLower,
                         @Nullable String alertUpper) {
        return tests.create(new TestMethodInput(code, name, ref, null, "NUMERIC", unit, replicates, decimals,
                num(lower), num(upper), null, num(alertLower), num(alertUpper), null, null, true)).id();
    }

    private UUID text(String code, String name, String ref, String limit) {
        return tests.create(new TestMethodInput(code, name, ref, null, "TEXT", null, 1, null, null, null, limit,
                null, null, null, null, false)).id();
    }

    private static SpecTestInput limit(Map<String, UUID> lib, String code, @Nullable String lower,
                                       @Nullable String upper, @Nullable String text) {
        return new SpecTestInput(lib.get(code), null, null, null, null, num(lower), num(upper), text, null, null, null);
    }

    private static List<TimePointInput> months(int[] values, int[] units) {
        List<TimePointInput> out = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            out.add(new TimePointInput(BigDecimal.valueOf(values[i]), "MONTH", null, units[i]));
        }
        return out;
    }

    private static @Nullable BigDecimal num(@Nullable String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private boolean exists(UUID tenant, String productCode) {
        return inTenant(tenant, () -> jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.product WHERE code = ?)")
                .param(productCode).query(Boolean.class).single());
    }

    private UUID siteId(UUID tenant, String code) {
        return inTenant(tenant, () -> jdbc.sql("SELECT id FROM app.site WHERE code = ? AND deleted_at IS NULL")
                .param(code).query(UUID.class).single());
    }

    private <T> T inTenant(UUID tenant, Supplier<T> query) {
        @Nullable T result = TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(_ -> query.get()));
        return Objects.requireNonNull(result);
    }
}
