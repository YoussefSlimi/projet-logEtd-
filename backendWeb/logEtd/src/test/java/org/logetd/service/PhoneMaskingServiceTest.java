package org.logetd.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneMaskingServiceTest {

    private final PhoneMaskingService service = new PhoneMaskingService();

    @Test
    void masksInternationalNumberWithSeparators() {
        assertEquals("Appelez +216****** vite",
                service.mask("Appelez +216 12 345 678 vite"));
    }

    @Test
    void masksNumberInQueryParameterStyle() {
        assertEquals("token=+216******", service.mask("token=+21612345678"));
    }

    @Test
    void masksLocalEightDigitNumber() {
        assertEquals("tel +216******", service.mask("tel 51234567"));
    }

    @Test
    void leavesOtherTextUnchanged() {
        assertEquals("Loyer 500000 millimes", service.mask("Loyer 500000 millimes"));
    }

    @Test
    void acceptsNull() {
        assertNull(service.mask(null));
    }
}
