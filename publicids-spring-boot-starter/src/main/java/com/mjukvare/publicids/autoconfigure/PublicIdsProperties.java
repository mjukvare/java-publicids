package com.mjukvare.publicids.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Descriptions are plain text, since IDEs show them as written in the generated configuration metadata.
/**
 * Configuration properties under {@code publicids}.
 *
 * @param install           Whether to install the codec behind the static PublicIds facade. Disable it when a
 *                           context needs a codec that differs from one already installed in the JVM, and inject
 *                           PublicIdCodec instead.
 * @param scan               Whether to scan the auto-configuration packages at startup for @PublicIdPrefix types
 *                           and @PublicIdRegistration declarations, so invalid or colliding prefixes fail the
 *                           startup instead of the first use. With scanning disabled, no @PublicIdRegistration
 *                           declarations are read.
 * @param annotationFallback Whether types that are neither scanned nor registered resolve through @PublicIdPrefix on
 *                           first use.
 */
@ConfigurationProperties("publicids")
public record PublicIdsProperties(
        @DefaultValue("true") boolean install,
        @DefaultValue("true") boolean scan,
        @DefaultValue("true") boolean annotationFallback) {
}
