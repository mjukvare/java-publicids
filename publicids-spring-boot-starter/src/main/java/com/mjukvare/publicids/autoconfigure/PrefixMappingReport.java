package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdCodec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Records where each prefix came from while the codec is built, and logs the resulting mapping at startup. Logs through
 * Commons Logging, like Spring itself, so the application's logging setup decides where it goes.
 */
final class PrefixMappingReport {

    private static final Log logger = LogFactory.getLog(PublicIdsAutoConfiguration.class);
    private static final String CUSTOMIZER = "PublicIdCodecCustomizer";

    private final Map<Class<?>, String> sources = new HashMap<>();

    void annotated(Collection<Class<?>> types) {
        types.forEach(type -> sources.putIfAbsent(type, "@PublicIdPrefix"));
    }

    void registered(PublicIdScanner.Registration registration) {
        sources.putIfAbsent(registration.type(), "@PublicIdRegistration on " + registration.declaredOn().getName());
    }

    // Anything the scan didn't record came from a customizer
    void log(PublicIdCodec codec, boolean annotationFallback) {
        if (!logger.isInfoEnabled()) return;

        var prefixes = codec.prefixes();
        var count = prefixes.size() + (prefixes.size() == 1 ? " type" : " types");
        var others = annotationFallback
                ? "With annotation fallback, other @PublicIdPrefix types resolve on first use."
                : "Annotation fallback is disabled, so no other type has a prefix.";
        logger.info("PublicIds maps " + count + " to prefixes. " + others);

        prefixes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> logger.info(entry.getValue() + " -> " + entry.getKey().getName()
                        + " (" + sources.getOrDefault(entry.getKey(), CUSTOMIZER) + ")"));
    }
}
