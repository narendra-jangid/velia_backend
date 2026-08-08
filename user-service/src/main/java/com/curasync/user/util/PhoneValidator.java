package com.curasync.user.util;

public final class PhoneValidator {

    private PhoneValidator() {}

    public static boolean isValid(String phone) {
        return phone != null && phone.matches("\\d{10}");
    }

    public static String normalize(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        return digits;
    }

}
