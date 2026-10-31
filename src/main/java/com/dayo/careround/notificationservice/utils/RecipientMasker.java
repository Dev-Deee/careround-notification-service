package com.dayo.careround.notificationservice.utils;

// Masks a phone number or email for admin views: "08061230001" -> "0806***0001", "ada@host.com" -> "a***@host.com".
public final class RecipientMasker {

    private RecipientMasker() {}

    public static String mask(String recipient) {
        if (recipient == null || recipient.isBlank()) return "";
        int at = recipient.indexOf('@');
        if (at >= 0) {
            return (at > 0 ? recipient.substring(0, 1) : "") + "***" + recipient.substring(at);
        }
        if (recipient.length() <= 8) {
            return recipient.substring(0, Math.min(2, recipient.length())) + "***";
        }
        return recipient.substring(0, 4) + "***" + recipient.substring(recipient.length() - 4);
    }
}
