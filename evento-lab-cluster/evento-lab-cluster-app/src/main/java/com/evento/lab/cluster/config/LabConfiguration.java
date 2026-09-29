package com.evento.lab.cluster.config;

import com.evento.lab.cluster.store.AppDatabase;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LabConfiguration {

    @Bean
    public LabSettings labSettings() {
        return LabSettings.fromEnvironment();
    }

    @Bean(destroyMethod = "close")
    public AppDatabase appDatabase(LabSettings settings) {
        return new AppDatabase(settings);
    }

    @Bean(destroyMethod = "close")
    public LabBundles labBundles(LabSettings settings, AppDatabase database) {
        var bundles = new LabBundles(settings, database);
        bundles.startAsync();
        return bundles;
    }

    /** DOWN until every bundle of the role has completed its handshake. */
    @Bean
    public HealthIndicator eventoBundlesHealth(LabBundles bundles) {
        return () -> {
            var b = bundles.isReady() ? Health.up() : Health.down();
            b.withDetail("role", bundles.settings().role().envValue());
            b.withDetail("bundles", bundles.started().keySet());
            b.withDetail("expected", bundles.expectedBundleIds());
            b.withDetail("busStates", bundles.busStates());
            if (bundles.lastError() != null) b.withDetail("lastError", bundles.lastError());
            return b.build();
        };
    }
}
