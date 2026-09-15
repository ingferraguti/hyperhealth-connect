package io.hyperhealth.connect.controlplane.inventory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.hyperhealth.connect.controlplane.inventory.api.VerifiedFacilityScopeArgumentResolver;
import io.hyperhealth.connect.controlplane.secret.JdbcSecretReferenceRepository;
import io.hyperhealth.connect.controlplane.secret.SecretReferenceRepository;

/** Fail-closed wiring for the scoped inventory slice and its bounded connection pool. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hhc.inventory", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ScopedInventoryConfiguration.PlatformDatabaseProperties.class)
public class ScopedInventoryConfiguration implements WebMvcConfigurer {

    private static final Duration MINIMUM_POOL_TIMEOUT = Duration.ofMillis(250);
    private static final Duration MINIMUM_CONNECTION_LIFETIME = Duration.ofSeconds(30);

    private final VerifiedFacilityScopeArgumentResolver scopeArgumentResolver =
            new VerifiedFacilityScopeArgumentResolver();

    @Bean(destroyMethod = "close")
    HikariDataSource platformDataSource(PlatformDatabaseProperties properties) {
        properties.validate();
        HikariConfig configuration = new HikariConfig();
        configuration.setPoolName("hhc-platform-db");
        configuration.setJdbcUrl(properties.jdbcUrl());
        configuration.setUsername(properties.username());
        configuration.setPassword(properties.password());
        configuration.setMaximumPoolSize(properties.maximumPoolSize());
        configuration.setMinimumIdle(properties.minimumIdle());
        configuration.setConnectionTimeout(properties.connectionTimeout().toMillis());
        configuration.setInitializationFailTimeout(properties.connectionTimeout().toMillis());
        configuration.setValidationTimeout(properties.validationTimeout().toMillis());
        configuration.setMaxLifetime(properties.maxLifetime().toMillis());
        configuration.setKeepaliveTime(properties.keepaliveTime().toMillis());
        configuration.addDataSourceProperty("ApplicationName", "hhc-control-plane");
        configuration.addDataSourceProperty("tcpKeepAlive", true);
        return new HikariDataSource(configuration);
    }

    @Bean
    ScopedEndpointRepository scopedEndpointRepository(DataSource platformDataSource) {
        return new JdbcScopedEndpointRepository(platformDataSource);
    }

    @Bean
    SecretReferenceRepository secretReferenceRepository(DataSource platformDataSource) {
        return new JdbcSecretReferenceRepository(platformDataSource);
    }

    @Bean
    ScopedInventoryService scopedInventoryService(ScopedEndpointRepository endpointRepository) {
        return new ScopedInventoryService(endpointRepository);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(scopeArgumentResolver);
    }

    @ConfigurationProperties("hhc.platform-database")
    public record PlatformDatabaseProperties(
            String jdbcUrl,
            String username,
            String password,
            int maximumPoolSize,
            int minimumIdle,
            Duration connectionTimeout,
            Duration validationTimeout,
            Duration maxLifetime,
            Duration keepaliveTime) {

        void validate() {
            requireText(jdbcUrl, "jdbcUrl");
            requireText(username, "username");
            Objects.requireNonNull(password, "password");
            if (password.isBlank()) {
                throw new IllegalStateException("password must not be blank when inventory is enabled");
            }
            if (maximumPoolSize < 1
                    || maximumPoolSize > 256
                    || minimumIdle < 0
                    || minimumIdle > maximumPoolSize) {
                throw new IllegalStateException("Invalid platform database pool size");
            }
            requirePositive(connectionTimeout, "connectionTimeout");
            requirePositive(validationTimeout, "validationTimeout");
            requirePositive(maxLifetime, "maxLifetime");
            requirePositive(keepaliveTime, "keepaliveTime");
            requireAtLeast(connectionTimeout, MINIMUM_POOL_TIMEOUT, "connectionTimeout");
            requireAtLeast(validationTimeout, MINIMUM_POOL_TIMEOUT, "validationTimeout");
            requireAtLeast(maxLifetime, MINIMUM_CONNECTION_LIFETIME, "maxLifetime");
            requireAtLeast(keepaliveTime, MINIMUM_CONNECTION_LIFETIME, "keepaliveTime");
            if (validationTimeout.compareTo(connectionTimeout) >= 0) {
                throw new IllegalStateException("validationTimeout must be shorter than connectionTimeout");
            }
            if (keepaliveTime.compareTo(maxLifetime) >= 0) {
                throw new IllegalStateException("keepaliveTime must be shorter than maxLifetime");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " must not be blank when inventory is enabled");
            }
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalStateException(name + " must be positive");
            }
        }

        private static void requireAtLeast(Duration value, Duration minimum, String name) {
            if (value.compareTo(minimum) < 0) {
                throw new IllegalStateException(name + " must be at least " + minimum);
            }
        }
    }
}
