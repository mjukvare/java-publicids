package com.mjukvare.publicids;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the public id prefix of a type. Applies to subclasses too, so proxies of the type resolve to it.
 * The prefix must match {@code [a-z0-9]+}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicIdPrefix {

    /** {@return the type's prefix, which must match {@code [a-z0-9]+}} */
    String value();
}
