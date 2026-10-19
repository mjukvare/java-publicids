package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdCodec;

/**
 * Adds to the auto-configured {@link PublicIdCodec} with code, for registrations that can't be declared up front:
 *
 * <pre>{@code
 * @Bean
 * PublicIdCodecCustomizer thirdPartyPrefixes() {
 *     return builder -> builder.register(ThirdPartyInvoice.class, "inv");
 * }
 * }</pre>
 *
 * For a fixed prefix, prefer {@link PublicIdRegistration}. A customizer is a bean, and test slices such as
 * {@code @WebMvcTest} don't load the configuration that declares it, so a slice builds a different codec unless it
 * {@code @Import}s that configuration.
 *
 * <p>Conflicting registrations fail at startup, so the order customizers run in doesn't change the result.
 */
@FunctionalInterface
public interface PublicIdCodecCustomizer {

    /**
     * Adds to the builder of the auto-configured codec, such as with
     * {@link PublicIdCodec.Builder#register Builder.register}.
     *
     * @param builder the builder, which already holds the prefixes found by scanning
     */
    void customize(PublicIdCodec.Builder builder);
}
