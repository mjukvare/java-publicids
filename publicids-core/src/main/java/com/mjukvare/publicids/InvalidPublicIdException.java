package com.mjukvare.publicids;

/**
 * Thrown by {@link PublicIdCodec#parseOrThrow PublicIdCodec.parseOrThrow} when text is not a well-formed public id of
 * the expected type: it is malformed, or it is the id of another type.
 *
 * <p>This is a problem with the input, not with the program, so it deliberately isn't an
 * {@link IllegalArgumentException}: the codec throws that when a type has no prefix, and a handler for bad input must
 * not catch that too.
 */
public final class InvalidPublicIdException extends RuntimeException {

    /** The type the public id was expected to belong to. */
    private final Class<?> type;
    /** The text that was rejected, exactly as given. */
    private final String publicId;

    /**
     * Creates an exception for text that was rejected as a public id of {@code type}.
     *
     * @param type     the type the public id was expected to belong to
     * @param publicId the text that was rejected, exactly as given
     */
    public InvalidPublicIdException(Class<?> type, String publicId) {
        super("Not a public id of " + type.getName() + ": \"" + publicId + "\"");
        this.type = type;
        this.publicId = publicId;
    }

    /** {@return the type the public id was expected to belong to} */
    public Class<?> type() {
        return type;
    }

    /** {@return the text that was rejected, exactly as given} */
    public String publicId() {
        return publicId;
    }
}
