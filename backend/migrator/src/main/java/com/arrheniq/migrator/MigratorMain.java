package com.arrheniq.migrator;

/**
 * CLI entry point (skeleton). Planned usage:
 * <pre>
 *   arrheniq-migrator --config migration.yaml --mode DRY_RUN
 * </pre>
 * Reads the source password from the environment (ESTAR_DB_PASSWORD), never from arguments.
 * Full wiring (YAML config, Spring context for TargetWriter) is built in Phase 6, epic E16.
 */
public final class MigratorMain {

    private MigratorMain() { }

    public static void main(String[] args) {
        System.out.println("arrheniq-migrator " + MigrationPipeline.MAPPING_VERSION
                + " (skeleton): see docs/data/migration-design.md");
    }
}
