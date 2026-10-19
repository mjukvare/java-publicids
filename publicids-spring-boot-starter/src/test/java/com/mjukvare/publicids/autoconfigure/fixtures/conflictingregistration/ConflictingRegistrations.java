package com.mjukvare.publicids.autoconfigure.fixtures.conflictingregistration;

import com.mjukvare.publicids.autoconfigure.PublicIdRegistration;
import com.mjukvare.publicids.autoconfigure.fixtures.thirdparty.VendorInvoice;

@PublicIdRegistration(type = VendorInvoice.class, prefix = "inv")
@PublicIdRegistration(type = VendorInvoice.class, prefix = "bill")
public final class ConflictingRegistrations {

    private ConflictingRegistrations() {}
}
