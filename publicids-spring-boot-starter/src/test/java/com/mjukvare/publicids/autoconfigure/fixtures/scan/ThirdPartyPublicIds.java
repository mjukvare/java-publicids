package com.mjukvare.publicids.autoconfigure.fixtures.scan;

import com.mjukvare.publicids.autoconfigure.PublicIdRegistration;
import com.mjukvare.publicids.autoconfigure.fixtures.thirdparty.VendorInvoice;
import com.mjukvare.publicids.autoconfigure.fixtures.thirdparty.VendorReceipt;

// Not a bean: the scan reads the declarations from the class itself
@PublicIdRegistration(type = VendorInvoice.class, prefix = "inv")
@PublicIdRegistration(type = VendorReceipt.class, prefix = "rct")
final class ThirdPartyPublicIds {

    private ThirdPartyPublicIds() {}
}
