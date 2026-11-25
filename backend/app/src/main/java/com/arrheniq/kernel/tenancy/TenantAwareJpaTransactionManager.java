package com.arrheniq.kernel.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * JPA transaction manager that binds the current {@link TenantContext} to every database
 * transaction (docs/architecture/multi-tenancy.md §3, rule 3):
 * <pre>
 *   SELECT set_config('app.tenant_id', :tenant, true)   -- transaction-local
 * </pre>
 * The setting resets at commit/rollback, so pooled connections never carry a tenant over.
 * If no context is bound, nothing is set and PostgreSQL row-level security makes every
 * tenant query fail closed (ARQ-TENANT-001). Works for JPA and JdbcClient access alike,
 * because HibernateJpaDialect exposes the JDBC connection of the JPA transaction.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, true)";

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantContextHolder.current().ifPresent(ctx -> bindTenant(ctx.tenantId().toString()));
    }

    private void bindTenant(String tenantId) {
        DataSource dataSource = getDataSource();
        if (dataSource == null) {
            throw new TransactionSystemException("ARQ-TENANT-002: no DataSource exposed by the JPA transaction manager");
        }
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        if (holder == null) {
            throw new TransactionSystemException("ARQ-TENANT-003: JDBC connection of the JPA transaction is not exposed");
        }
        Connection connection = holder.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(SET_TENANT_SQL)) {
            ps.setString(1, tenantId);
            ps.execute();
        } catch (SQLException e) {
            throw new TransactionSystemException("ARQ-TENANT-004: could not bind tenant to transaction", e);
        }
    }
}
