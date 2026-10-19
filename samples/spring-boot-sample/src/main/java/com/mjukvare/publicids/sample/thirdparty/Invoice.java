package com.mjukvare.publicids.sample.thirdparty;

import java.math.BigDecimal;
import java.util.UUID;

// Stands in for a type from a library you depend on, which is why it has a package of its own: you can't put
// @PublicIdPrefix on it, so configuration.ThirdPartyPublicIds declares its prefix instead.
public final class Invoice {

    private final UUID id;
    private final UUID customerId;
    private final BigDecimal amount;

    public Invoice(UUID id, UUID customerId, BigDecimal amount) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
