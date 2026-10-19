package com.mjukvare.publicids;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PublicIdCodecTest {

    private static final UUID ZERO = new UUID(0, 0);
    private static final String ZERO_ENCODED = "A".repeat(22);

    @PublicIdPrefix("usr")
    static class User {}

    static class UserProxy extends User {}

    @PublicIdPrefix("adm")
    static class Admin extends User {}

    @PublicIdPrefix("usr")
    static class Impostor {}

    @PublicIdPrefix("Not_Valid")
    static class BadlyAnnotated {}

    static class Invoice {}

    static class InvoiceProxy extends Invoice {}

    static class Order {}

    private static PublicIdCodec annotationsOnly() {
        return PublicIdCodec.builder().build();
    }

    @Nested
    class Encoding {

        @Test
        void prefixesTheEncodedUuid() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            String result = codec.of(User.class, ZERO);

            // Assert
            assertThat(result).isEqualTo("usr_" + ZERO_ENCODED);
        }

        @Test
        void matchesDocumentedExample() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            var id = UUID.fromString("0192f8a4-3b7c-7d1e-9f2a-6c5b8e4d1a37");

            // Act
            String result = codec.of(User.class, id);

            // Assert
            assertThat(result).isEqualTo("usr_AZL4pDt8fR6fKmxbjk0aNw");
        }

        @Test
        void usesUrlSafeAlphabet() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            String result = codec.of(User.class, new UUID(-1, -1));

            // Assert
            assertThat(result).isEqualTo("usr_" + "_".repeat(21) + "w");
        }

        @Test
        void usesExplicitRegistration() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(Invoice.class, "inv").build();

            // Act
            String result = codec.of(Invoice.class, ZERO);

            // Assert
            assertThat(result).isEqualTo("inv_" + ZERO_ENCODED);
        }

        @Test
        void throwsForTypeWithoutPrefix() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Throwable result = catchThrowable(() -> codec.of(Invoice.class, ZERO));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Invoice.class.getName())
                    .hasMessageContaining("@PublicIdPrefix")
                    .hasMessageContaining("register(");
        }
    }

    @Nested
    class Parsing {

        @Test
        void parseOrThrowReturnsUuid() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            String publicId = codec.of(User.class, ZERO);

            // Act
            UUID result = codec.parseOrThrow(User.class, publicId);

            // Assert
            assertThat(result).isEqualTo(ZERO);
        }

        @Test
        void parseOrThrowRejectsIdOfAnotherType() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            String adminId = codec.of(Admin.class, ZERO);

            // Act
            Throwable result = catchThrowable(() -> codec.parseOrThrow(User.class, adminId));

            // Assert
            assertThat(result)
                    .isInstanceOfSatisfying(InvalidPublicIdException.class, e -> {
                        assertThat(e.type()).isEqualTo(User.class);
                        assertThat(e.publicId()).isEqualTo(adminId);
                    })
                    .hasMessageContaining(User.class.getName());
        }

        @Test
        void parseOrThrowRejectsMalformedId() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Throwable result = catchThrowable(() -> codec.parseOrThrow(User.class, "usr_not-an-id"));

            // Assert
            assertThat(result).isInstanceOf(InvalidPublicIdException.class);
        }

        @Test
        void parseOrThrowStillThrowsIllegalArgumentForTypeWithoutPrefix() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Throwable result = catchThrowable(() -> codec.parseOrThrow(Invoice.class, "inv_" + ZERO_ENCODED));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .isNotInstanceOf(InvalidPublicIdException.class);
        }

        @Test
        void roundTrips() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            var id = UUID.randomUUID();
            String publicId = codec.of(User.class, id);

            // Act
            Optional<UUID> result = codec.parse(User.class, publicId);

            // Assert
            assertThat(result).contains(id);
        }

        @Test
        void rejectsIdOfAnotherType() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            String adminId = codec.of(Admin.class, ZERO);

            // Act
            Optional<UUID> result = codec.parse(User.class, adminId);

            // Assert
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "usr",
                "usr_",
                "AAAAAAAAAAAAAAAAAAAAAA",
                "usr-AAAAAAAAAAAAAAAAAAAAAA",
                "USR_AAAAAAAAAAAAAAAAAAAAAA",
                "usr_AAAAAAAAAAAAAAAAAAAAA",
                "usr_AAAAAAAAAAAAAAAAAAAAAAA",
                "usr_AAAAAAAAAAAAAAAAAAAAAAAA",
                "usr_AAAAAAAAAAAAAAAAAAAAAA==",
                "usr_++++++++++++++++++++++",
                "usr_AAAAAAAAAAAAAAAAAAAA A",
                " usr_AAAAAAAAAAAAAAAAAAAAAA",
        })
        void rejectsMalformedIds(String publicId) {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Optional<UUID> result = codec.parse(User.class, publicId);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void rejectsNonCanonicalEncoding() {
            // Arrange
            // The last character's low four bits are unused: "B" decodes to the same bytes as "A".
            PublicIdCodec codec = annotationsOnly();
            String publicId = "usr_" + "A".repeat(21) + "B";

            // Act
            Optional<UUID> result = codec.parse(User.class, publicId);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void throwsForTypeWithoutPrefix() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Throwable result = catchThrowable(() -> codec.parse(Invoice.class, "inv_" + ZERO_ENCODED));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Invoice.class.getName());
        }
    }

    @Nested
    class Resolution {

        @Test
        void subclassUsesSuperclassAnnotation() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            String result = codec.of(UserProxy.class, ZERO);

            // Assert
            assertThat(result).isEqualTo("usr_" + ZERO_ENCODED);
        }

        @Test
        void subclassParsesIdOfSuperclass() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            String userId = codec.of(User.class, ZERO);

            // Act
            Optional<UUID> result = codec.parse(UserProxy.class, userId);

            // Assert
            assertThat(result).contains(ZERO);
        }

        @Test
        void subclassAnnotationOverridesSuperclass() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            String result = codec.of(Admin.class, ZERO);

            // Assert
            assertThat(result).startsWith("adm_");
        }

        @Test
        void subclassUsesSuperclassRegistration() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(Invoice.class, "inv").build();

            // Act
            String result = codec.of(InvoiceProxy.class, ZERO);

            // Assert
            assertThat(result).startsWith("inv_");
        }

        @Test
        void subclassRegistrationOverridesSuperclassAnnotation() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(UserProxy.class, "prx").build();

            // Act
            String result = codec.of(UserProxy.class, ZERO);

            // Assert
            assertThat(result).startsWith("prx_");
        }

        @Test
        void superclassKeepsAnnotationWhenSubclassIsRegistered() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(UserProxy.class, "prx").build();

            // Act
            String result = codec.of(User.class, ZERO);

            // Assert
            assertThat(result).startsWith("usr_");
        }

        @Test
        void annotatedTypeThrowsWhenAnnotationFallbackIsDisabled() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().annotationFallback(false).build();

            // Act
            Throwable result = catchThrowable(() -> codec.of(User.class, ZERO));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Annotation fallback is disabled");
        }

        @Test
        void eagerlyAnnotatedTypeWorksWhenAnnotationFallbackIsDisabled() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().annotationFallback(false).annotated(User.class).build();

            // Act
            String result = codec.of(User.class, ZERO);

            // Assert
            assertThat(result).startsWith("usr_");
        }

        @Test
        void annotatedPrefixAlreadyUsedByAnotherAnnotatedTypeThrows() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();
            codec.of(User.class, ZERO);

            // Act
            Throwable result = catchThrowable(() -> codec.of(Impostor.class, ZERO));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Impostor.class.getName())
                    .hasMessageContaining(User.class.getName());
        }

        @Test
        void annotatedPrefixAlreadyUsedByRegistrationThrows() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(Invoice.class, "usr").build();

            // Act
            Throwable result = catchThrowable(() -> codec.of(User.class, ZERO));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(User.class.getName())
                    .hasMessageContaining(Invoice.class.getName());
        }

        @Test
        void invalidAnnotatedPrefixThrows() {
            // Arrange
            PublicIdCodec codec = annotationsOnly();

            // Act
            Throwable result = catchThrowable(() -> codec.of(BadlyAnnotated.class, ZERO));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not_Valid");
        }

        @Test
        void encodesWithCustomEncoder() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();

            // Act
            String result = codec.of(User.class, UUID.randomUUID());

            // Assert
            assertThat(result).hasSize("usr_".length() + 32);
        }

        @Test
        void parsesWithCustomEncoder() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();
            var id = UUID.randomUUID();
            String publicId = codec.of(User.class, id);

            // Act
            Optional<UUID> result = codec.parse(User.class, publicId);

            // Assert
            assertThat(result).contains(id);
        }

        @Test
        void rejectsNonCanonicalEncodingOfCustomEncoder() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();
            String publicId = codec.of(User.class, UUID.randomUUID());
            String upperCased = publicId.toUpperCase().replace("USR_", "usr_");

            // Act
            Optional<UUID> result = codec.parse(User.class, upperCased);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Building {

        @ParameterizedTest
        @ValueSource(strings = {"", "USR", "us_r", "us r", "usr-", "ø"})
        void rejectsInvalidPrefix(String prefix) {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder();

            // Act
            Throwable result = catchThrowable(() -> builder.register(Invoice.class, prefix));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("[a-z0-9]+");
        }

        @Test
        void allowsRegisteringTypeAgainWithSamePrefix() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder().register(Invoice.class, "inv");

            // Act
            PublicIdCodec result = builder.register(Invoice.class, "inv").build();

            // Assert
            assertThat(result.prefixes()).containsExactlyEntriesOf(Map.of(Invoice.class, "inv"));
        }

        @Test
        void rejectsRegisteringTypeAgainWithDifferentPrefix() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder().register(Invoice.class, "inv");

            // Act
            Throwable result = catchThrowable(() -> builder.register(Invoice.class, "bill"));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered with prefix \"inv\"");
        }

        @Test
        void rejectsSamePrefixForTwoTypes() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder().register(Invoice.class, "inv");

            // Act
            Throwable result = catchThrowable(() -> builder.register(Order.class, "inv"));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Order.class.getName())
                    .hasMessageContaining(Invoice.class.getName());
        }

        @Test
        void rejectsRegistrationThatContradictsAnnotation() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder();

            // Act
            Throwable result = catchThrowable(() -> builder.register(User.class, "person"));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@PublicIdPrefix(\"usr\")");
        }

        @Test
        void allowsRegistrationThatMatchesAnnotation() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder()
                    .register(User.class, "usr")
                    .annotated(User.class)
                    .build();

            // Act
            String result = codec.of(User.class, ZERO);

            // Assert
            assertThat(result).startsWith("usr_");
        }

        @Test
        void acceptsCollectionOfBoundedClassTypes() {
            // Arrange
            // Only compiles while annotated(...) takes Collection<? extends Class<?>>
            List<Class<? extends User>> types = List.of(Admin.class);

            // Act
            PublicIdCodec result = PublicIdCodec.builder().annotated(types).build();

            // Assert
            assertThat(result.prefixes()).containsEntry(Admin.class, "adm");
        }

        @Test
        void rejectsEagerlyAnnotatedTypeWithoutAnnotation() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder();

            // Act
            Throwable result = catchThrowable(() -> builder.annotated(Invoice.class));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not annotated");
        }

        @Test
        void rejectsEagerlyAnnotatedTypeWithInvalidPrefix() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder();

            // Act
            Throwable result = catchThrowable(() -> builder.annotated(List.of(BadlyAnnotated.class)));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not_Valid");
        }

        @Test
        void rejectsEagerlyAnnotatedTypesSharingPrefix() {
            // Arrange
            PublicIdCodec.Builder builder = PublicIdCodec.builder();

            // Act
            Throwable result = catchThrowable(() -> builder.annotated(User.class, Impostor.class));

            // Assert
            assertThat(result)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("\"usr\"");
        }

        @Test
        void prefixesContainsRegisteredAndEagerlyAnnotatedTypesOnly() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder()
                    .register(Invoice.class, "inv")
                    .annotated(User.class)
                    .build();
            codec.of(Admin.class, ZERO);

            // Act
            Map<Class<?>, String> result = codec.prefixes();

            // Assert
            assertThat(result).containsOnly(
                    Map.entry(Invoice.class, "inv"),
                    Map.entry(User.class, "usr"));
        }

        @Test
        void prefixesIsUnmodifiable() {
            // Arrange
            PublicIdCodec codec = PublicIdCodec.builder().register(Invoice.class, "inv").build();
            Map<Class<?>, String> prefixes = codec.prefixes();

            // Act
            Throwable result = catchThrowable(() -> prefixes.put(Order.class, "ord"));

            // Assert
            assertThat(result).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Equality {

        @Test
        void codecsWithSameConfigurationAreEqualRegardlessOfOrder() {
            // Arrange
            PublicIdCodec first = PublicIdCodec.builder().register(Invoice.class, "inv").annotated(User.class).build();

            // Act
            PublicIdCodec result = PublicIdCodec.builder().annotated(User.class).register(Invoice.class, "inv").build();

            // Assert
            assertThat(result).isEqualTo(first).hasSameHashCodeAs(first);
        }

        @Test
        void codecsWithDifferentRegistrationsAreNotEqual() {
            // Arrange
            PublicIdCodec first = PublicIdCodec.builder().register(Invoice.class, "inv").build();

            // Act
            PublicIdCodec result = PublicIdCodec.builder().register(Invoice.class, "bill").build();

            // Assert
            assertThat(result).isNotEqualTo(first);
        }

        @Test
        void codecsWithDifferentAnnotationFallbackAreNotEqual() {
            // Arrange
            PublicIdCodec first = PublicIdCodec.builder().build();

            // Act
            PublicIdCodec result = PublicIdCodec.builder().annotationFallback(false).build();

            // Assert
            assertThat(result).isNotEqualTo(first);
        }

        @Test
        void codecsWithDifferentEncodersAreNotEqual() {
            // Arrange
            PublicIdCodec first = PublicIdCodec.builder().build();

            // Act
            PublicIdCodec result = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();

            // Assert
            assertThat(result).isNotEqualTo(first);
        }

        @Test
        void codecsWithEqualEncoderInstancesAreEqual() {
            // Arrange
            PublicIdCodec first = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();

            // Act
            PublicIdCodec result = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();

            // Assert
            assertThat(result).isEqualTo(first).hasSameHashCodeAs(first);
        }

        @Test
        void lazilyResolvedTypesDoNotAffectEquality() {
            // Arrange
            PublicIdCodec first = annotationsOnly();
            PublicIdCodec second = annotationsOnly();

            // Act
            first.of(User.class, ZERO);

            // Assert
            assertThat(first).isEqualTo(second);
        }
    }

    // Shows the codec does not assume Base64's length: 16 bytes become 32 characters.
    record HexIdEncoder() implements IdEncoder {
        @Override
        public String encode(byte[] id) {
            return HexFormat.of().formatHex(id);
        }

        @Override
        public byte[] decode(String encodedId) {
            return HexFormat.of().parseHex(encodedId);
        }
    }
}
