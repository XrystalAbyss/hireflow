package com.hireflow.db;

import com.hireflow.exception.DatabaseException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Service orchestrating controlled database migrations via Flyway.
 */
public class FlywayMigrationService {

    private final DataSource dataSource;
    private final String[] locations;

    public FlywayMigrationService(DataSource dataSource) {
        this(dataSource, "classpath:db/migration");
    }

    public FlywayMigrationService(DataSource dataSource, String... locations) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
        this.locations = locations != null && locations.length > 0
                ? locations
                : new String[]{"classpath:db/migration"};
    }

    private Flyway buildFlyway(boolean allowClean) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .cleanDisabled(!allowClean)
                .load();
    }

    /**
     * Executes pending migrations against the configured database.
     *
     * @return MigrateResult containing details on applied versions
     */
    public MigrateResult migrate() {
        try {
            return buildFlyway(false).migrate();
        } catch (Exception ex) {
            throw new DatabaseException("Flyway migration execution failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates applied migrations against current migration scripts.
     */
    public void validate() {
        try {
            buildFlyway(false).validate();
        } catch (Exception ex) {
            throw new DatabaseException("Flyway schema validation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Cleans all schema objects. MUST be restricted to test environments!
     */
    public void clean() {
        try {
            buildFlyway(true).clean();
        } catch (Exception ex) {
            throw new DatabaseException("Flyway schema clean failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieves current migration metadata.
     */
    public MigrationInfoService getInfo() {
        try {
            return buildFlyway(false).info();
        } catch (Exception ex) {
            throw new DatabaseException("Failed to retrieve Flyway migration info: " + ex.getMessage(), ex);
        }
    }
}
