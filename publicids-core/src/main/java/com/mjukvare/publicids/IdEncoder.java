package com.mjukvare.publicids;

/**
 * Turns the 16 bytes of a UUID into text and back. {@link #decode decode} throws {@link IllegalArgumentException} for
 * text it cannot decode. Implementations must be thread-safe, since a codec is shared across threads. Codecs compare
 * their encoders with {@code equals}, so implement the encoder as a record, or implement {@code equals}, if codecs
 * using it should compare equal.
 */
public interface IdEncoder {

    /**
     * Writes the bytes of a UUID as text. The same bytes must always give the same text.
     *
     * @param id the 16 bytes of a UUID, most significant first
     * @return the text that follows the prefix and underscore in a public id
     */
    String encode(byte[] id);

    /**
     * Reads back the bytes written by {@link #encode encode}.
     *
     * @param encodedId the text that follows the prefix and underscore in a public id
     * @return the decoded bytes; the codec rejects any count other than 16
     * @throws IllegalArgumentException if {@code encodedId} can't be decoded
     */
    byte[] decode(String encodedId);
}
