package org.logetd.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywaySchemaConfig {

    /**
     * Hibernate ({@code ddl-auto=update}) crée les tables au démarrage du contexte.
     * La migration Flyway est donc reportée après ce schéma, sinon {@code ALTER TABLE bookings} échoue.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
        };
    }

    @Bean
    public ApplicationRunner flywayAfterHibernate(Flyway flyway) {
        return args -> flyway.migrate();
    }
}
