package com.arrheniq.devsupport;

import com.arrheniq.identity.UserInvitationService;
import com.arrheniq.identity.UserInvitationService.Invitation;
import com.arrheniq.identity.UserInvitationService.RoleAssignment;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Creates the local tenants and invites the Keycloak dev-realm users (infra/compose) so a developer can
 * log in right after {@code docker compose up}. Idempotent. Tenants are provisioned with the schema
 * owner (as the platform console would); users are invited through the real, audited service.
 * Local passwords only; this class is active only with {@code --spring.profiles.active=local}.
 */
@Component
@Profile("local")
class LocalDevData implements ApplicationRunner {

    static final UUID TENANT_A = UUID.fromString("a0000000-0000-4000-8000-00000000000a");
    static final UUID TENANT_B = UUID.fromString("b0000000-0000-4000-8000-00000000000b");

    private static final Logger LOG = LoggerFactory.getLogger(LocalDevData.class);
    private static final AuditActor SEED = AuditActor.system("local-dev-data");

    private final Environment env;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final UserInvitationService invitations;
    private final TransactionTemplate tx;

    LocalDevData(Environment env, JdbcClient jdbc, AuditService audit, UserInvitationService invitations,
                 PlatformTransactionManager txManager) {
        this.env = env;
        this.jdbc = jdbc;
        this.audit = audit;
        this.invitations = invitations;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionIfMissing(TENANT_A, "tenant-a", "Tenant A Pharma Inc.", "Tenant A", "NJ", "New Jersey QC");
        provisionIfMissing(TENANT_B, "tenant-b", "Tenant B Labs LLC", "Tenant B", "NC", "North Carolina QC");
        inviteIfMissing(TENANT_A, new Invitation("tadmin", "tadmin@tenant-a.local", "Tara Admin",
                List.of(new RoleAssignment("TENANT_ADMIN", null))));
        inviteIfMissing(TENANT_A, new Invitation("smanager", "smanager@tenant-a.local", "Sam Manager",
                List.of(new RoleAssignment("STABILITY_MANAGER", "NJ"))));
        inviteIfMissing(TENANT_A, new Invitation("qapprover", "qapprover@tenant-a.local", "Quinn Approver",
                List.of(new RoleAssignment("APPROVER_QA", "NJ"))));
        inviteIfMissing(TENANT_B, new Invitation("bmanager", "bmanager@tenant-b.local", "Blake Manager",
                List.of(new RoleAssignment("STABILITY_MANAGER", "NC"))));
    }

    private void provisionIfMissing(UUID id, String slug, String legal, String display, String site, String siteName) {
        Boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM platform.tenant WHERE id = ?)")
                .param(id).query(Boolean.class).single();
        if (exists) {
            return;
        }
        DriverManagerDataSource owner = new DriverManagerDataSource(env.getRequiredProperty("spring.datasource.url"),
                env.getRequiredProperty("spring.flyway.user"), env.getRequiredProperty("spring.flyway.password"));
        JdbcClient.create(owner)
                .sql("SELECT platform.provision_tenant(?, ?, ?, ?, ?, ?, 'America/New_York', 'PROFESSIONAL',"
                        + " 'local-dev-data', true)")
                .params(id, slug, legal, display, site, siteName)
                .query((rs, n) -> 1)
                .single();
        LOG.info("Local dev tenant provisioned: {}", slug);
    }

    private void inviteIfMissing(UUID tenant, Invitation invitation) {
        TenantContextHolder.runAs(TenantContext.system(tenant), () -> tx.executeWithoutResult(status -> {
            Boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.app_user WHERE username = ?)")
                    .param(invitation.username()).query(Boolean.class).single();
            if (!exists) {
                audit.open("devdata.invite");
                invitations.invite(SEED, invitation);
                LOG.info("Local dev user invited: {} (log in with the Keycloak dev realm)", invitation.username());
            }
        }));
    }
}
