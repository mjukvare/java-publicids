package com.mjukvare.publicids.autoconfigure.fixtures.scan;

import com.mjukvare.publicids.PublicIdPrefix;

import java.util.UUID;

@PublicIdPrefix("ord")
public record Order(UUID id) {
}
