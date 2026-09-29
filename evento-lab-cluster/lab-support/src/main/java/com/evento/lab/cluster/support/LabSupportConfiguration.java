package com.evento.lab.cluster.support;

import com.evento.lab.cluster.support.web.LabInfoController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Spring side of a lab bundle. A bundle application imports this and
 * declares one {@link BundleDefinition} bean; its {@code application.properties}
 * names the default bundle id ({@code lab.bundle.default-id}), which
 * {@code EVENTO_BUNDLE_ID} overrides.
 */
@Configuration
@Import(LabInfoController.class)
public class LabSupportConfiguration {

    @Bean
    public BundleDeployment bundleDeployment(@Value("${lab.bundle.default-id}") String defaultBundleId) {
        return BundleDeployment.fromEnvironment(defaultBundleId);
    }

    @Bean(destroyMethod = "close")
    public LabDatabases labDatabases(BundleDeployment deployment) {
        return new LabDatabases(deployment);
    }

    @Bean(destroyMethod = "close")
    public BundleRunner bundleRunner(BundleDeployment deployment, BundleDefinition definition, LabDatabases databases) {
        var runner = new BundleRunner(deployment, definition, databases);
        runner.startAsync();
        return runner;
    }

    /** DOWN until the bundle completed its handshake: compose healthchecks and 'up --wait' gate on it. */
    @Bean
    public HealthIndicator eventoBundleHealth(BundleRunner runner) {
        return () -> {
            var b = runner.isReady() ? Health.up() : Health.down();
            b.withDetail("bundle", runner.deployment().bundleId());
            b.withDetail("instance", runner.deployment().instanceId());
            b.withDetail("busState", runner.busState());
            if (runner.lastError() != null) b.withDetail("lastError", runner.lastError());
            return b.build();
        };
    }
}
