package com.mjukvare.publicids;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PublicIdsTest {

    private static final UUID ZERO = new UUID(0, 0);

    @PublicIdPrefix("usr")
    static class User {}

    static class Customer {}

    private static PublicIdCodec customerCodec() {
        return PublicIdCodec.builder().register(Customer.class, "cus").build();
    }

    @BeforeEach
    void resetBefore() {
        PublicIds.reset();
    }

    @AfterEach
    void resetAfter() {
        PublicIds.reset();
    }

    @Test
    void defaultCodecUsesAnnotationsWhenNothingIsInstalled() {
        // Act
        String result = PublicIds.of(User.class, ZERO);

        // Assert
        assertThat(result).startsWith("usr_");
    }

    @Test
    void defaultCodecParsesWhenNothingIsInstalled() {
        // Arrange
        String publicId = PublicIds.of(User.class, ZERO);

        // Act
        Optional<UUID> result = PublicIds.parse(User.class, publicId);

        // Assert
        assertThat(result).contains(ZERO);
    }

    @Test
    void parseOrThrowUsesActiveCodec() {
        // Arrange
        PublicIds.install(customerCodec());
        String publicId = PublicIds.of(Customer.class, ZERO);

        // Act
        UUID result = PublicIds.parseOrThrow(Customer.class, publicId);

        // Assert
        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void defaultCodecHasNoRegistrationsWhenNothingIsInstalled() {
        // Act
        Throwable result = catchThrowable(() -> PublicIds.of(Customer.class, ZERO));

        // Assert
        assertThat(result).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesInstalledCodec() {
        // Arrange
        PublicIdCodec codec = customerCodec();
        PublicIds.install(codec);

        // Act
        PublicIdCodec result = PublicIds.codec();

        // Assert
        assertThat(result).isSameAs(codec);
    }

    @Test
    void installedCodecUsesRegistrations() {
        // Arrange
        PublicIds.install(customerCodec());

        // Act
        String result = PublicIds.of(Customer.class, ZERO);

        // Assert
        assertThat(result).startsWith("cus_");
    }

    @Test
    void installedCodecFallsBackToAnnotations() {
        // Arrange
        PublicIds.install(customerCodec());

        // Act
        String result = PublicIds.of(User.class, ZERO);

        // Assert
        assertThat(result).startsWith("usr_");
    }

    @Test
    void installAfterDefaultUseThrowsWithFirstUseAsCause() {
        // Arrange
        PublicIds.of(User.class, ZERO);

        // Act
        Throwable result = catchThrowable(() -> PublicIds.install(customerCodec()));

        // Assert
        assertThat(result)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before a codec was installed")
                .cause()
                .satisfies(cause -> assertThat(cause.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().equals(PublicIdsTest.class.getName())
                                && frame.getMethodName().equals("installAfterDefaultUseThrowsWithFirstUseAsCause")));
    }

    @Test
    void installingDifferentCodecThrows() {
        // Arrange
        PublicIds.install(customerCodec());
        PublicIdCodec different = PublicIdCodec.builder().register(Customer.class, "cst").build();

        // Act
        Throwable result = catchThrowable(() -> PublicIds.install(different));

        // Assert
        assertThat(result)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A different PublicIdCodec is already installed");
    }

    @Test
    void equalCodecStaysInstalledUntilEveryInstallationIsClosed() {
        // Arrange
        PublicIdCodec first = customerCodec();
        PublicIds.Installation firstInstallation = PublicIds.install(first);
        PublicIds.install(customerCodec());
        firstInstallation.close();

        // Act
        PublicIdCodec result = PublicIds.codec();

        // Assert
        assertThat(result).isSameAs(first);
    }

    @Test
    void equalCodecIsUninstalledOnceEveryInstallationIsClosed() {
        // Arrange
        PublicIds.Installation firstInstallation = PublicIds.install(customerCodec());
        PublicIds.Installation secondInstallation = PublicIds.install(customerCodec());
        firstInstallation.close();
        secondInstallation.close();

        // Act
        PublicIdCodec result = PublicIds.codec();

        // Assert
        assertThat(result).isEqualTo(PublicIdCodec.builder().build());
    }

    @Test
    void closingInstallationTwiceReleasesItOnce() {
        // Arrange
        PublicIdCodec codec = customerCodec();
        PublicIds.Installation firstInstallation = PublicIds.install(codec);
        PublicIds.install(codec);
        firstInstallation.close();
        firstInstallation.close();

        // Act
        PublicIdCodec result = PublicIds.codec();

        // Assert
        assertThat(result).isSameAs(codec);
    }

    @Test
    void differentCodecCanBeInstalledOnceEveryInstallationIsClosed() {
        // Arrange
        PublicIds.install(customerCodec()).close();
        PublicIdCodec different = PublicIdCodec.builder().register(Customer.class, "cst").build();
        PublicIds.install(different);

        // Act
        String result = PublicIds.of(Customer.class, ZERO);

        // Assert
        assertThat(result).startsWith("cst_");
    }

    @Test
    void defaultUseBetweenInstallationsDoesNotBlockNextInstallation() {
        // Arrange
        PublicIds.install(customerCodec()).close();
        PublicIds.of(User.class, ZERO);
        PublicIdCodec different = PublicIdCodec.builder().register(Customer.class, "cst").build();

        // Act
        Throwable result = catchThrowable(() -> PublicIds.install(different));

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void installationAfterDefaultUseBetweenInstallationsReplacesDefault() {
        // Arrange
        PublicIds.install(customerCodec()).close();
        PublicIds.of(User.class, ZERO);
        PublicIdCodec different = PublicIdCodec.builder().register(Customer.class, "cst").build();
        PublicIds.install(different);

        // Act
        PublicIdCodec result = PublicIds.codec();

        // Assert
        assertThat(result).isSameAs(different);
    }
}
