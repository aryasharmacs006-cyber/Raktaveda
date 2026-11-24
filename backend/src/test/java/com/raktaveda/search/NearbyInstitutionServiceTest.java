package com.raktaveda.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.raktaveda.common.ApiException;
import org.junit.jupiter.api.Test;

class NearbyInstitutionServiceTest {

    @Test
    void fillsInDefaults() {
        var q = NearbyInstitutionService.validate(23.2599, 77.4126, null, null, null);
        assertEquals(10.0, q.radiusKm());
        assertEquals(20, q.limit());
        assertEquals(null, q.type());
    }

    @Test
    void acceptsTypeInAnyCase() {
        assertEquals("BLOOD_BANK", NearbyInstitutionService.validate(23.0, 77.0, 5.0, " blood_bank ", 10).type());
    }

    @Test
    void rejectsBadLocation() {
        assertEquals("LOCATION_INVALID", code(null, 77.0, null, null, null));
        assertEquals("LOCATION_INVALID", code(91.0, 77.0, null, null, null));
        assertEquals("LOCATION_INVALID", code(23.0, 181.0, null, null, null));
    }

    @Test
    void rejectsBadRadiusTypeAndLimit() {
        assertEquals("RADIUS_INVALID", code(23.0, 77.0, 0.0, null, null));
        assertEquals("RADIUS_INVALID", code(23.0, 77.0, 51.0, null, null));
        assertEquals("TYPE_INVALID", code(23.0, 77.0, null, "SHOP", null));
        assertEquals("VALIDATION_FAILED", code(23.0, 77.0, null, null, 0));
        assertEquals("VALIDATION_FAILED", code(23.0, 77.0, null, null, 51));
    }

    private static String code(Double lat, Double lon, Double radius, String type, Integer limit) {
        return assertThrows(ApiException.class, () -> NearbyInstitutionService.validate(lat, lon, radius, type, limit)).getCode();
    }
}
