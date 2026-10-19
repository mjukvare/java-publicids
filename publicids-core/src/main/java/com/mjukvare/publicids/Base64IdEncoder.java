package com.mjukvare.publicids;

import java.util.Base64;

/**
 * The default {@link IdEncoder}: writes the 16 bytes of a UUID as 22 characters of URL-safe Base64 without padding, so
 * public ids can go in URLs as they are.
 */
public record Base64IdEncoder() implements IdEncoder {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @Override
    public String encode(byte[] id) {
        return ENCODER.encodeToString(id);
    }

    @Override
    public byte[] decode(String encodedId) {
        return DECODER.decode(encodedId);
    }
}
