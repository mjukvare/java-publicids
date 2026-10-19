package com.mjukvare.publicids;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Converts UUIDs to public ids and back: with the prefix {@code usr}, {@code 0192f8a4-3b7c-7d1e-9f2a-6c5b8e4d1a37}
 * becomes {@code usr_AZL4pDt8fR6fKmxbjk0aNw}. Immutable and thread-safe, so build one and share it.
 *
 * {@snippet :
 * @PublicIdPrefix("usr")
 * class User { }
 *
 * var codec = PublicIdCodec.builder()
 *         .annotated(User.class)            // reads @PublicIdPrefix now, so mistakes fail the build() call
 *         .register(Invoice.class, "inv")   // for types you can't or don't want to annotate
 *         .build();
 *
 * String publicId = codec.of(User.class, user.id());             // "usr_AZL4pDt8fR6fKmxbjk0aNw"
 * Optional<UUID> id = codec.parse(User.class, publicId);         // the UUID, or empty for bad input
 * UUID userId = codec.parseOrThrow(User.class, request.userId()); // the UUID, or InvalidPublicIdException
 * }
 *
 * <p>To use a codec where it can't be injected, such as in an entity, install it behind {@link PublicIds}.
 *
 * <p>In tests, build a real codec rather than mocking one. It does no I/O and is cheap to build, and the test then
 * checks the public ids your code actually produces. Bad input needs no stubbing either: {@link #parse parse} and
 * {@link #parseOrThrow parseOrThrow} reject it just as they do in production.
 *
 * <h2>Format</h2>
 *
 * A public id is the type's prefix, an underscore, and the UUID's 16 bytes as written by the {@link IdEncoder}. The
 * default {@link Base64IdEncoder} writes 22 characters of URL-safe Base64 without padding, so public ids can go in URLs
 * as they are. Treat them as opaque: they are case-sensitive, so compare them exactly, and use {@link #parse parse}
 * rather than taking them apart yourself.
 *
 * <p>A public id stays valid only as long as its type keeps its prefix and the codec keeps its encoder. Change either
 * and ids already handed out no longer parse.
 *
 * <h2>Prefixes</h2>
 *
 * A prefix matches {@code [a-z0-9]+}. A type gets one from {@link Builder#register Builder.register}, from
 * {@link Builder#annotated Builder.annotated}, or, unless {@linkplain Builder#annotationFallback annotation fallback}
 * is disabled, from its {@link PublicIdPrefix} on first use.
 *
 * <p>A type's prefix is resolved by walking from the type up its superclass chain (interfaces are not consulted). At
 * each level an explicit registration is checked first, then {@link PublicIdPrefix} (unless annotation fallback is
 * disabled). The first match wins. So subclasses, including proxies generated at runtime, share their superclass's
 * prefix unless they declare their own, and {@code of(entity.getClass(), id)} works on a proxied entity.
 *
 * <p>Each prefix belongs to one type. The builder rejects two types with the same prefix. A clash it can't see, because
 * annotation fallback finds the second type later, throws {@link IllegalStateException} when that type is first used.
 *
 * <h2>Errors</h2>
 *
 * Text that is not a public id of the expected type is bad input, not a bug: {@link #parse parse} returns empty for it
 * and {@link #parseOrThrow parseOrThrow} throws {@link InvalidPublicIdException}. The other exceptions point at a
 * mistake in the program and should not be handled as bad input:
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} when a type has no prefix,
 *   <li>{@link IllegalStateException} when a type's annotated prefix is already used by another type,
 *   <li>{@link NullPointerException} for a {@code null} argument.
 * </ul>
 *
 * <h2>Equality</h2>
 *
 * Two codecs are equal when they were built with the same registrations, the same annotation fallback setting and equal
 * encoders, so they turn every UUID into the same public id. {@link PublicIds#install PublicIds.install} relies on
 * this.
 */
public final class PublicIdCodec {

    private static final Pattern VALID_PREFIX = Pattern.compile("[a-z0-9]+");
    private static final String SEPARATOR = "_";
    private static final int UUID_BYTES = 16;

    private final Settings settings;
    // Prefix -> owning class name. Names rather than classes, so the codec doesn't pin classloaders.
    private final ConcurrentMap<String, String> owners = new ConcurrentHashMap<>();
    private final ClassValue<String> prefixes = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return resolve(type);
        }
    };

    private PublicIdCodec(Builder builder) {
        this.settings = new Settings(Map.copyOf(builder.registrations), builder.annotationFallback, builder.encoder);
        settings.registrations().forEach((type, prefix) -> owners.put(prefix, type.getName()));
    }

    /** {@return a builder for a codec with no registrations, annotation fallback enabled and the {@link Base64IdEncoder}} */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the public id for {@code id}, an id of {@code type}. The same type and UUID always give the same public id.
     *
     * @param type the type the id belongs to
     * @param id   the UUID to encode
     * @return the public id, such as {@code usr_AZL4pDt8fR6fKmxbjk0aNw}
     * @throws IllegalArgumentException if no prefix can be resolved for {@code type}
     * @throws IllegalStateException if {@code type}'s annotated prefix is already used by another type
     */
    public String of(Class<?> type, UUID id) {
        Objects.requireNonNull(id, "id");
        var prefix = prefix(type);
        var bytes = ByteBuffer.allocate(UUID_BYTES)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits());

        return prefix + SEPARATOR + settings.encoder().encode(bytes.array());
    }

    /**
     * Returns the UUID in {@code publicId}, for callers that expect bad input and handle it themselves. Parsing is
     * strict: it accepts exactly what {@link #of of} writes for {@code type}, so an id of another type, one that
     * differs in case, or one with extra characters is rejected. Types that share a prefix through inheritance accept
     * each other's ids.
     *
     * @param type     the type the public id should belong to
     * @param publicId the text to parse
     * @return the UUID, or empty if {@code publicId} is not a well-formed public id of {@code type}
     * @throws IllegalArgumentException if no prefix can be resolved for {@code type}
     * @throws IllegalStateException if {@code type}'s annotated prefix is already used by another type
     */
    public Optional<UUID> parse(Class<?> type, String publicId) {
        Objects.requireNonNull(publicId, "publicId");
        var prefix = prefix(type) + SEPARATOR;
        if (!publicId.startsWith(prefix)) return Optional.empty();

        var encoded = publicId.substring(prefix.length());
        var encoder = settings.encoder();
        byte[] bytes;
        try {
            bytes = encoder.decode(encoded);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (bytes.length != UUID_BYTES) return Optional.empty();
        // Encodings can have unused bits (22 Base64 characters hold 132 bits for 128), so several strings may decode
        // to the same UUID. Accept only the one we would write.
        if (!encoder.encode(bytes).equals(encoded)) return Optional.empty();

        var buffer = ByteBuffer.wrap(bytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    /**
     * Like {@link #parse parse}, for callers that treat a bad public id as an error, such as a web endpoint that maps
     * {@link InvalidPublicIdException} to 400 Bad Request.
     *
     * @param type     the type the public id should belong to
     * @param publicId the text to parse
     * @return the UUID
     * @throws InvalidPublicIdException if {@code publicId} is not a well-formed public id of {@code type}
     * @throws IllegalArgumentException if no prefix can be resolved for {@code type}
     * @throws IllegalStateException if {@code type}'s annotated prefix is already used by another type
     */
    public UUID parseOrThrow(Class<?> type, String publicId) {
        return parse(type, publicId).orElseThrow(() -> new InvalidPublicIdException(type, publicId));
    }

    /**
     * The prefixes known up front: explicit registrations and types passed to
     * {@link Builder#annotated Builder.annotated}. Types resolved later through annotation fallback are not included.
     *
     * @return an unmodifiable map from each type to its prefix
     */
    public Map<Class<?>, String> prefixes() {
        return settings.registrations();
    }

    private String prefix(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return prefixes.get(type);
    }

    private String resolve(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            var registered = settings.registrations().get(c);
            if (registered != null) return registered;
            if (settings.annotationFallback()) {
                var annotation = c.getDeclaredAnnotation(PublicIdPrefix.class);
                if (annotation != null) return claim(c, annotation.value());
            }
        }

        var hint = settings.annotationFallback()
                ? "Annotate it with @PublicIdPrefix or register it with PublicIdCodec.builder().register(...)"
                : "Annotation fallback is disabled; register it with PublicIdCodec.builder().register(...) or .annotated(...)";
        throw new IllegalArgumentException("No public id prefix for " + type.getName() + ". " + hint);
    }

    private String claim(Class<?> type, String prefix) {
        requireValidPrefix(type, prefix);
        var owner = owners.putIfAbsent(prefix, type.getName());
        if (owner != null && !owner.equals(type.getName())) {
            throw new IllegalStateException(
                    "Public id prefix \"" + prefix + "\" of " + type.getName() + " is already used by " + owner);
        }

        return prefix;
    }

    private static void requireValidPrefix(Class<?> type, String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (!VALID_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                    "Invalid public id prefix \"" + prefix + "\" for " + type.getName() + ": must match [a-z0-9]+");
        }
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PublicIdCodec that && settings.equals(that.settings);
    }

    @Override
    public int hashCode() {
        return settings.hashCode();
    }

    @Override
    public String toString() {
        return "PublicIdCodec{prefixes=" + settings.registrations()
                + ", annotationFallback=" + settings.annotationFallback()
                + ", encoder=" + settings.encoder() + "}";
    }

    // Everything that defines how the codec behaves. Equality derives from it, so a new setting can't be left out.
    private record Settings(Map<Class<?>, String> registrations, boolean annotationFallback, IdEncoder encoder) {}

    /**
     * Collects prefixes for a {@link PublicIdCodec}. Conflicts throw as soon as they are added, so the result never
     * depends on the order of calls. Registering a type again with the same prefix is allowed.
     */
    public static final class Builder {
        private final Map<Class<?>, String> registrations = new HashMap<>();
        private final Map<String, Class<?>> owners = new HashMap<>();
        private boolean annotationFallback = true;
        private IdEncoder encoder = new Base64IdEncoder();

        private Builder() {}

        /**
         * Gives {@code type} the prefix {@code prefix}, without annotating it. Subclasses of {@code type} get it too,
         * unless they have a prefix of their own. If {@code type} is also annotated, the two prefixes must agree.
         *
         * @param type   the type to give the prefix
         * @param prefix the prefix, which must match {@code [a-z0-9]+}
         * @return this builder
         * @throws IllegalArgumentException if the prefix is invalid, differs from {@code type}'s own
         *                                  {@link PublicIdPrefix}, or conflicts with an earlier registration
         */
        public Builder register(Class<?> type, String prefix) {
            Objects.requireNonNull(type, "type");
            requireValidPrefix(type, prefix);
            var annotation = type.getDeclaredAnnotation(PublicIdPrefix.class);
            if (annotation != null && !annotation.value().equals(prefix)) {
                throw new IllegalArgumentException(type.getName() + " is annotated with @PublicIdPrefix(\""
                        + annotation.value() + "\") but registered with \"" + prefix + "\"");
            }

            return add(type, prefix);
        }

        /**
         * Reads the {@link PublicIdPrefix} of each type now rather than on first use, so problems surface at build time.
         * The types are then listed in {@link PublicIdCodec#prefixes PublicIdCodec.prefixes}, and they keep their
         * prefix with annotation fallback disabled.
         *
         * @param types the types, each annotated with {@link PublicIdPrefix}
         * @return this builder
         * @throws IllegalArgumentException if a type is not annotated, its prefix is invalid, or it conflicts with an
         *                                  earlier registration
         */
        public Builder annotated(Class<?>... types) {
            return annotated(Arrays.asList(types));
        }

        /**
         * Like {@link #annotated(Class[])}, for a collection of types, such as the result of a classpath scan.
         *
         * @param types the types, each annotated with {@link PublicIdPrefix}
         * @return this builder
         * @throws IllegalArgumentException if a type is not annotated, its prefix is invalid, or it conflicts with an
         *                                  earlier registration
         */
        // The wildcard is for Class's type argument, not subclasses of Class: without it, a
        // List<Class<? extends BaseEntity>> would not be accepted.
        @SuppressWarnings("java:S4968")
        public Builder annotated(Collection<? extends Class<?>> types) {
            for (var type : types) {
                Objects.requireNonNull(type, "type");
                var annotation = type.getDeclaredAnnotation(PublicIdPrefix.class);
                if (annotation == null) {
                    throw new IllegalArgumentException(type.getName() + " is not annotated with @PublicIdPrefix");
                }
                requireValidPrefix(type, annotation.value());
                add(type, annotation.value());
            }

            return this;
        }

        /**
         * Whether types that are not registered resolve through {@link PublicIdPrefix}. Defaults to {@code true}.
         * Disable it to require that every type is passed to {@link #register register} or
         * {@link #annotated annotated}: any other type, annotated or not, then fails with
         * {@link IllegalArgumentException}.
         *
         * @param enabled whether to resolve types that are not registered through {@link PublicIdPrefix}
         * @return this builder
         */
        public Builder annotationFallback(boolean enabled) {
            this.annotationFallback = enabled;
            return this;
        }

        /**
         * How the UUID part of a public id is written. Defaults to {@link Base64IdEncoder}. Changing the encoder of a
         * codec in use invalidates every public id it has handed out.
         *
         * @param encoder the encoder to use
         * @return this builder
         */
        public Builder encoder(IdEncoder encoder) {
            this.encoder = Objects.requireNonNull(encoder, "encoder");
            return this;
        }

        /** {@return a codec with the builder's current settings} Changes made to the builder afterwards don't affect it. */
        public PublicIdCodec build() {
            return new PublicIdCodec(this);
        }

        private Builder add(Class<?> type, String prefix) {
            var existing = registrations.get(type);
            if (existing != null && !existing.equals(prefix)) {
                throw new IllegalArgumentException(type.getName() + " is already registered with prefix \""
                        + existing + "\", cannot register it with \"" + prefix + "\"");
            }
            var owner = owners.get(prefix);
            if (owner != null && owner != type) {
                throw new IllegalArgumentException("Public id prefix \"" + prefix + "\" of " + type.getName()
                        + " is already used by " + owner.getName());
            }

            registrations.put(type, prefix);
            owners.put(prefix, type);
            return this;
        }
    }
}
