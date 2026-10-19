package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdCodec;
import com.mjukvare.publicids.PublicIds;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

/**
 * Builds a {@link PublicIdCodec} from the {@code @PublicIdPrefix} types and {@link PublicIdRegistration} declarations
 * in the auto-configuration packages and any {@link PublicIdCodecCustomizer} beans, and installs it behind
 * {@link PublicIds}. A {@code PublicIdCodec} bean of your own replaces the built one and is installed instead.
 */
@AutoConfiguration
@EnableConfigurationProperties(PublicIdsProperties.class)
public class PublicIdsAutoConfiguration {

    /** Called by Spring Boot when it applies the auto-configuration. */
    public PublicIdsAutoConfiguration() {}

    @Bean
    @ConditionalOnMissingBean
    PublicIdCodec publicIdCodec(PublicIdsProperties properties,
                                BeanFactory beanFactory,
                                Environment environment,
                                ResourceLoader resourceLoader,
                                ObjectProvider<PublicIdCodecCustomizer> customizers) {
        var builder = PublicIdCodec.builder().annotationFallback(properties.annotationFallback());
        var report = new PrefixMappingReport();
        if (properties.scan() && AutoConfigurationPackages.has(beanFactory)) {
            var scanner = new PublicIdScanner(environment, resourceLoader);
            var result = scanner.scan(AutoConfigurationPackages.get(beanFactory));
            builder.annotated(result.annotatedTypes());
            report.annotated(result.annotatedTypes());
            result.registrations().forEach(registration -> {
                registration.applyTo(builder);
                report.registered(registration);
            });
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));

        var codec = builder.build();
        report.log(codec, properties.annotationFallback());
        return codec;
    }

    // Never lazy: nothing depends on this bean, so with lazy initialization the codec would never be installed.
    // Spring closes the installation when the context shuts down.
    @Bean
    @Lazy(false)
    @ConditionalOnBooleanProperty(name = "publicids.install", matchIfMissing = true)
    PublicIds.Installation publicIdsInstallation(PublicIdCodec codec) {
        return PublicIds.install(codec);
    }
}
