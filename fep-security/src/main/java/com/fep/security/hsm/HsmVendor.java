package com.fep.security.hsm;

/**
 * Supported HSM vendors.
 */
public enum HsmVendor {

    /** Thales payShield HSM */
    THALES("Thales", "payShield"),

    /** Utimaco HSM */
    UTIMACO("Utimaco", "CryptoServer"),

    /** SafeNet/Gemalto Luna HSM */
    SAFENET("SafeNet", "Luna"),

    /** Software HSM for development/testing */
    SOFTWARE("Software", "SoftHSM");

    private final String vendorName;
    private final String productLine;

    HsmVendor(String vendorName, String productLine) {
        this.vendorName = vendorName;
        this.productLine = productLine;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getProductLine() {
        return productLine;
    }
}
