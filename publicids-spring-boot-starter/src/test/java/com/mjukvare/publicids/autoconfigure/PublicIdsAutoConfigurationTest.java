package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdCodec;
import com.mjukvare.publicids.PublicIdPrefix;
import com.mjukvare.publicids.PublicIds;
import com.mjukvare.publicids.autoconfigure.fixtures.collision.CollisionConfiguration;
import com.mjukvare.publicids.autoconfigure.fixtures.conflictingregistration.ConflictingRegistrationConfiguration;
import com.mjukvare.publicids.autoconfigure.fixtures.conflictingregistration.ConflictingRegistrations;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.AbstractAccount;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.Customer;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.Order;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.Outer;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.SavingsAccount;
import com.mjukvare.publicids.autoconfigure.fixtures.scan.ScanConfiguration;
import com.mjukvare.publicids.autoconfigure.fixtures.thirdparty.VendorInvoice;
import com.mjukvare.publicids.autoconfigure.fixtures.thirdparty.VendorReceipt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// PublicIds is JVM-wide. Every context here closes at the end of its run, which uninstalls its codec, and no test
// touches the facade without an installed codec, so the default codec is never locked in.
class PublicIdsAutoConfigurationTest {

    private static final UUID ID = UUID.fromString("0192f8a4-3b7c-7d1e-9f2a-6c5b8e4d1a37");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PublicIdsAutoConfiguration.class));

    @PublicIdPrefix("usr")
    static class User {}

    static class ThirdPartyInvoice {}

    @Configuration(proxyBeanMethods = false)
    static class InvoiceCustomizerConfiguration {
        @Bean
        PublicIdCodecCustomizer invoicePrefix() {
            return builder -> builder.register(ThirdPartyInvoice.class, "inv");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ContradictingCustomizerConfiguration {
        @Bean
        PublicIdCodecCustomizer userPrefix() {
            return builder -> builder.register(User.class, "person");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CodecConfiguration {
        @Bean
        PublicIdCodec customCodec() {
            return PublicIdCodec.builder().register(ThirdPartyInvoice.class, "bill").build();
        }
    }

    private static PublicIdCodec unrelatedCodec() {
        return PublicIdCodec.builder().register(ThirdPartyInvoice.class, "unrelated").build();
    }

    @Nested
    class Codec {

        @Test
        void resolvesAnnotatedTypes() {
            runner.run(context -> {
                // Arrange
                var codec = context.getBean(PublicIdCodec.class);

                // Act
                var result = codec.of(User.class, ID);

                // Assert
                assertThat(result).isEqualTo("usr_AZL4pDt8fR6fKmxbjk0aNw");
            });
        }

        @Test
        void appliesCustomizers() {
            runner.withUserConfiguration(InvoiceCustomizerConfiguration.class).run(context -> {
                // Arrange
                var codec = context.getBean(PublicIdCodec.class);

                // Act
                var result = codec.of(ThirdPartyInvoice.class, ID);

                // Assert
                assertThat(result).startsWith("inv_");
            });
        }

        @Test
        void backsOffForUserDefinedCodec() {
            runner.withUserConfiguration(CodecConfiguration.class).run(context -> {
                // Act
                var result = context.getBean(PublicIdCodec.class).prefixes();

                // Assert
                assertThat(result).containsExactlyEntriesOf(Map.of(ThirdPartyInvoice.class, "bill"));
            });
        }

        @Test
        void disablesAnnotationFallback() {
            runner.withPropertyValues("publicids.annotation-fallback=false").run(context -> {
                // Arrange
                var codec = context.getBean(PublicIdCodec.class);

                // Act
                var result = catchThrowable(() -> codec.of(User.class, ID));

                // Assert
                assertThat(result)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Annotation fallback is disabled");
            });
        }

        @Test
        void failsStartupWhenCustomizerContradictsAnnotation() {
            runner.withUserConfiguration(ContradictingCustomizerConfiguration.class).run(context -> {
                // Act
                var result = context.getStartupFailure();

                // Assert
                assertThat(result)
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("@PublicIdPrefix(\"usr\")");
            });
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class Logging {

        @Test
        void logsHowManyTypesAreMapped(CapturedOutput output) {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Assert
                assertThat(output).contains("PublicIds maps 6 types to prefixes. With annotation fallback");
            });
        }

        @Test
        void logsScannedTypeWithItsSource(CapturedOutput output) {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Assert
                assertThat(output).contains("cus -> " + Customer.class.getName() + " (@PublicIdPrefix)");
            });
        }

        @Test
        void logsDeclaredRegistrationWithItsDeclaringClass(CapturedOutput output) {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Assert
                assertThat(output).contains("inv -> " + VendorInvoice.class.getName()
                        + " (@PublicIdRegistration on com.mjukvare.publicids.autoconfigure.fixtures.scan.ThirdPartyPublicIds)");
            });
        }

        @Test
        void logsCustomizerRegistration(CapturedOutput output) {
            runner.withUserConfiguration(InvoiceCustomizerConfiguration.class).run(context -> {
                // Assert
                assertThat(output).contains("inv -> " + ThirdPartyInvoice.class.getName() + " (PublicIdCodecCustomizer)");
            });
        }

        @Test
        void logsThatOtherTypesHaveNoPrefixWithoutAnnotationFallback(CapturedOutput output) {
            runner.withPropertyValues("publicids.annotation-fallback=false").run(context -> {
                // Assert
                assertThat(output).contains("PublicIds maps 0 types to prefixes. Annotation fallback is disabled");
            });
        }
    }

    @Nested
    class Scanning {

        @Test
        void registersAnnotatedTypesAndDeclaredRegistrationsInAutoConfigurationPackages() {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Act
                var result = context.getBean(PublicIdCodec.class).prefixes();

                // Assert
                assertThat(result).containsOnly(
                        Map.entry(Customer.class, "cus"),
                        Map.entry(AbstractAccount.class, "acc"),
                        Map.entry(Order.class, "ord"),
                        Map.entry(Outer.Nested.class, "nst"),
                        Map.entry(VendorInvoice.class, "inv"),
                        Map.entry(VendorReceipt.class, "rct"));
            });
        }

        @Test
        void declaredRegistrationResolves() {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Arrange
                var codec = context.getBean(PublicIdCodec.class);

                // Act
                var result = codec.of(VendorInvoice.class, ID);

                // Assert
                assertThat(result).startsWith("inv_");
            });
        }

        @Test
        void failsStartupOnConflictingRegistrationsNamingTheDeclaringClass() {
            runner.withUserConfiguration(ConflictingRegistrationConfiguration.class).run(context -> {
                // Act
                var result = context.getStartupFailure();

                // Assert
                assertThat(result)
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(VendorInvoice.class.getName())
                        .hasMessageContaining("declared by @PublicIdRegistration on " + ConflictingRegistrations.class.getName());
            });
        }

        @Test
        void subclassOfScannedTypeResolvesThroughSuperclass() {
            runner.withUserConfiguration(ScanConfiguration.class).run(context -> {
                // Arrange
                var codec = context.getBean(PublicIdCodec.class);

                // Act
                var result = codec.of(SavingsAccount.class, ID);

                // Assert
                assertThat(result).startsWith("acc_");
            });
        }

        @Test
        void canBeDisabled() {
            runner.withUserConfiguration(ScanConfiguration.class)
                    .withPropertyValues("publicids.scan=false")
                    .run(context -> {
                        // Act
                        var result = context.getBean(PublicIdCodec.class).prefixes();

                        // Assert
                        assertThat(result).isEmpty();
                    });
        }

        @Test
        void annotationFallbackStillResolvesWhenDisabled() {
            runner.withUserConfiguration(ScanConfiguration.class)
                    .withPropertyValues("publicids.scan=false")
                    .run(context -> {
                        // Arrange
                        var codec = context.getBean(PublicIdCodec.class);

                        // Act
                        var result = codec.of(Customer.class, ID);

                        // Assert
                        assertThat(result).startsWith("cus_");
                    });
        }

        @Test
        void failsStartupOnCollidingPrefixes() {
            runner.withUserConfiguration(CollisionConfiguration.class).run(context -> {
                // Act
                var result = context.getStartupFailure();

                // Assert
                assertThat(result)
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("\"dup\"");
            });
        }
    }

    @Nested
    class Installation {

        @Test
        void installsCodec() {
            runner.run(context -> {
                // Act
                var result = PublicIds.codec();

                // Assert
                assertThat(result).isSameAs(context.getBean(PublicIdCodec.class));
            });
        }

        @Test
        void facadeUsesCustomizedCodec() {
            runner.withUserConfiguration(InvoiceCustomizerConfiguration.class).run(context -> {
                // Act
                var result = PublicIds.of(ThirdPartyInvoice.class, ID);

                // Assert
                assertThat(result).startsWith("inv_");
            });
        }

        @Test
        void installsUserDefinedCodec() {
            runner.withUserConfiguration(CodecConfiguration.class).run(context -> {
                // Act
                var result = PublicIds.codec();

                // Assert
                assertThat(result).isSameAs(context.getBean(PublicIdCodec.class));
            });
        }

        @Test
        void installsEagerlyWithLazyInitialization() {
            runner.withPropertyValues("spring.main.lazy-initialization=true").run(context -> {
                // Act
                var result = PublicIds.codec();

                // Assert
                assertThat(result).isSameAs(context.getBean(PublicIdCodec.class));
            });
        }

        @Test
        void uninstallsWhenContextCloses() {
            // Arrange
            runner.run(context -> {});

            // Act
            var result = catchThrowable(() -> PublicIds.install(unrelatedCodec()).close());

            // Assert
            assertThat(result).isNull();
        }

        @Test
        void canBeDisabled() {
            runner.withPropertyValues("publicids.install=false").run(context -> {
                // Assert
                assertThat(context).doesNotHaveBean(PublicIds.Installation.class);
            });
        }

        @Test
        void leavesFacadeUnconfiguredWhenDisabled() {
            runner.withPropertyValues("publicids.install=false").run(context -> {
                // Act
                var result = catchThrowable(() -> PublicIds.install(unrelatedCodec()).close());

                // Assert
                assertThat(result).isNull();
            });
        }

        @Test
        void startsSecondContextWithEqualCodec() {
            runner.run(first -> runner.run(second -> {
                // Act
                var result = second.getStartupFailure();

                // Assert
                assertThat(result).isNull();
            }));
        }

        @Test
        void failsStartupWhenDifferentCodecIsInstalled() {
            runner.run(first -> runner.withUserConfiguration(InvoiceCustomizerConfiguration.class).run(second -> {
                // Act
                var result = second.getStartupFailure();

                // Assert
                assertThat(result)
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("A different PublicIdCodec is already installed");
            }));
        }
    }
}
