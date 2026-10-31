package com.dayo.careround.notificationservice.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipientMaskerTest {

    @Test
    void masksPhoneNumbers() {
        assertEquals("0806***0001", RecipientMasker.mask("08061230001"));
        assertEquals("+234***0001", RecipientMasker.mask("+2348061230001"));
        assertEquals("12***", RecipientMasker.mask("12345"));
    }

    @Test
    void masksEmails() {
        assertEquals("a***@host.com", RecipientMasker.mask("ada@host.com"));
        assertEquals("***@host.com", RecipientMasker.mask("@host.com"));
    }

    @Test
    void blankIsEmpty() {
        assertEquals("", RecipientMasker.mask(null));
        assertEquals("", RecipientMasker.mask(" "));
    }
}
