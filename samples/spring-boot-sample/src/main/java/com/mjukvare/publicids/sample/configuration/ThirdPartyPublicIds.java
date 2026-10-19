package com.mjukvare.publicids.sample.configuration;

import com.mjukvare.publicids.autoconfigure.PublicIdRegistration;
import com.mjukvare.publicids.sample.thirdparty.Invoice;

// Prefixes for types you can't annotate. Your own types just carry @PublicIdPrefix; see Customer.
// This class is never a bean: the starter reads the declarations when it scans the application's packages, so every
// context, test slices included, builds the same codec. There is no need to build or install the codec yourself.
@PublicIdRegistration(type = Invoice.class, prefix = "inv")
final class ThirdPartyPublicIds {

    private ThirdPartyPublicIds() {}
}
