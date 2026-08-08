package com.curasync.user.util;

public final class PincodeValidator {

    private PincodeValidator() {}

    /** Indian postal pincode — 6 digits. Also accepts "zip" / "zipcode" aliases at the API layer. */
    public static boolean isValid(String pincode) {
        return pincode != null && pincode.matches("\\d{6}");
    }

}
