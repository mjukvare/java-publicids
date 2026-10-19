package com.mjukvare.publicids.sample.domain;

import com.mjukvare.publicids.PublicIdPrefix;
import com.mjukvare.publicids.PublicIds;

import java.util.UUID;

// Internally a customer is identified by its UUID. Outside the application it is only ever known by its public id,
// such as cus_AZL4pDt8fR6fKmxbjk0aNw.
@PublicIdPrefix("cus")
public record Customer(UUID id, String name) {

    // Domain objects aren't Spring beans, so they use the static facade rather than an injected PublicIdCodec
    public String publicId() {
        return PublicIds.of(Customer.class, id);
    }
}
