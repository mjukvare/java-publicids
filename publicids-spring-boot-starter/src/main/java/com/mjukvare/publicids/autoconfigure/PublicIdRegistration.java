package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdPrefix;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the public id prefix of a type you can't annotate with {@link PublicIdPrefix}, such as one from another
 * library. Put it on any class in your application's packages; the starter finds it when it scans them at startup:
 *
 * <pre>{@code
 * @PublicIdRegistration(type = Invoice.class, prefix = "inv")
 * @PublicIdRegistration(type = Receipt.class, prefix = "rct")
 * final class ThirdPartyPublicIds {}
 * }</pre>
 *
 * Unlike a {@link PublicIdCodecCustomizer} bean, a declaration doesn't depend on which beans a context loads, so test
 * slices such as {@code @WebMvcTest} get the same codec as the application. Declarations are only read while
 * {@code publicids.scan} is enabled, which is the default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(PublicIdRegistration.List.class)
public @interface PublicIdRegistration {

    /** {@return the type to give the prefix} */
    Class<?> type();

    /** {@return the type's prefix, which must match {@code [a-z0-9]+}} */
    String prefix();

    /** Holds repeated {@link PublicIdRegistration} declarations. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface List {

        /** {@return the repeated declarations} */
        PublicIdRegistration[] value();
    }
}
