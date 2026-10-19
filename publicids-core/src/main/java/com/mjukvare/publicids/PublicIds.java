package com.mjukvare.publicids;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Static access to a single, JVM-wide {@link PublicIdCodec}, for code that can't have a codec injected, such as
 * entities, value objects and serializers. Thread-safe.
 *
 * {@snippet :
 * @PublicIdPrefix("usr")
 * class User {
 *     private UUID id;
 *
 *     public String publicId() {
 *         return PublicIds.of(User.class, id);
 *     }
 * }
 * }
 *
 * <p>Where a codec can be injected, such as in Spring-managed beans, inject {@link PublicIdCodec} instead. That keeps
 * the dependency visible and doesn't depend on what has been installed.
 *
 * <h2>Which codec is used</h2>
 *
 * Until the first codec is {@linkplain #install installed}, the first use locks in a default codec that resolves
 * prefixes from {@link PublicIdPrefix} only, the same as {@code PublicIdCodec.builder().build()}. After that,
 * installing a codec fails, because earlier calls would have used different rules than later ones. The exception's
 * cause shows where the default codec was first used.
 *
 * <p>So if you need registrations or another encoder, install the codec at startup, before anything uses this class,
 * and close the installation at shutdown:
 *
 * {@snippet :
 * var codec = PublicIdCodec.builder().register(Invoice.class, "inv").build();
 * try (var installation = PublicIds.install(codec)) {
 *     runApplication();
 * }
 * }
 *
 * <p>{@code publicids-spring-boot-starter} does this for you: it installs the application's codec when the context
 * starts and closes the installation when the context shuts down. Set {@code publicids.install=false} to turn that off.
 *
 * <p>Installations are counted, so several owners can share one codec, such as Spring test contexts cached in the same
 * JVM. Installing an equal codec again succeeds, and the codec stays installed until every installation is closed.
 * Installing a different codec while one is installed fails. Once the last installation is closed, calls use the
 * default codec until the next installation, but don't lock it in: the lock-in catches code that runs before startup
 * installs the codec, while a gap between installations, such as between two test contexts, is expected. The next
 * installation can then be any codec.
 */
public final class PublicIds {

    private static final Object LOCK = new Object();

    // PublicIdCodec is immutable and thread-safe, so a volatile reference is enough to publish it safely.
    @SuppressWarnings("java:S3077")
    private static volatile @Nullable PublicIdCodec active;

    // Guarded by LOCK
    private static @Nullable PublicIdCodec installed;
    private static int installations;
    private static boolean everInstalled;
    private static @Nullable Throwable defaultFirstUse;

    private PublicIds() {}

    /**
     * {@link PublicIdCodec#of PublicIdCodec.of} with the {@linkplain #codec active codec}.
     *
     * @param type the type the id belongs to
     * @param id   the UUID to encode
     * @return the public id
     */
    public static String of(Class<?> type, UUID id) {
        return codec().of(type, id);
    }

    /**
     * {@link PublicIdCodec#parse PublicIdCodec.parse} with the {@linkplain #codec active codec}.
     *
     * @param type     the type the public id should belong to
     * @param publicId the text to parse
     * @return the UUID, or empty if {@code publicId} is not a well-formed public id of {@code type}
     */
    public static Optional<UUID> parse(Class<?> type, String publicId) {
        return codec().parse(type, publicId);
    }

    /**
     * {@link PublicIdCodec#parseOrThrow PublicIdCodec.parseOrThrow} with the {@linkplain #codec active codec}.
     *
     * @param type     the type the public id should belong to
     * @param publicId the text to parse
     * @return the UUID
     */
    public static UUID parseOrThrow(Class<?> type, String publicId) {
        return codec().parseOrThrow(type, publicId);
    }

    /**
     * The active codec: the installed one, or else the default. Calling this counts as a use, so it locks in the
     * default codec if no codec has been installed yet.
     *
     * @return the active codec
     */
    public static PublicIdCodec codec() {
        var codec = active;
        if (codec != null) return codec;

        synchronized (LOCK) {
            codec = active;
            if (codec == null) {
                // Only a use before the first installation locks in the default. Between installations, such as
                // between two test contexts, the default fills the gap without blocking the next installation.
                if (!everInstalled) {
                    defaultFirstUse = new Throwable("PublicIds was first used here, with the default codec");
                }
                codec = PublicIdCodec.builder().build();
                active = codec;
            }
            return codec;
        }
    }

    /**
     * Installs {@code codec} until the returned installation is closed. Installing an equal codec again is allowed and
     * counted; the codec stays installed until every installation is closed. Codecs are compared with
     * {@link PublicIdCodec#equals PublicIdCodec.equals}.
     *
     * @param codec the codec to install
     * @return the installation; close it when the codec should no longer be installed
     * @throws IllegalStateException if the default codec was used before the first installation, or a different codec
     *                               is installed
     */
    public static Installation install(PublicIdCodec codec) {
        Objects.requireNonNull(codec, "codec");
        synchronized (LOCK) {
            if (defaultFirstUse != null) {
                throw new IllegalStateException("PublicIds was used with the default codec before a codec was installed. "
                        + "Install the codec at startup, before first use; in Spring-managed beans, inject PublicIdCodec "
                        + "instead. The cause shows the first use.", defaultFirstUse);
            }
            if (installed == null) {
                installed = codec;
                active = codec;
            } else if (!installed.equals(codec)) {
                throw new IllegalStateException(
                        "A different PublicIdCodec is already installed: " + installed + ". Refusing " + codec);
            }

            installations++;
            everInstalled = true;
            return new Installation();
        }
    }

    // For tests in this package, which can't otherwise undo locking in the default codec.
    static void reset() {
        synchronized (LOCK) {
            active = null;
            installed = null;
            installations = 0;
            everInstalled = false;
            defaultFirstUse = null;
        }
    }

    /** Closing uninstalls the codec once no other installation of it is open. Closing again has no effect. */
    public static final class Installation implements AutoCloseable {
        // Guarded by LOCK
        private boolean closed;

        private Installation() {}

        @Override
        public void close() {
            synchronized (LOCK) {
                if (closed) return;

                closed = true;
                if (--installations > 0) return;

                installed = null;
                active = null;
            }
        }
    }
}
