package com.raktaveda.dataimport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstitutionRowValidatorTest {

    private static Map<String, String> good() {
        Map<String, String> m = new HashMap<>();
        m.put("name", "TEST DATA - Example Hospital");
        m.put("type", "HOSPITAL");
        m.put("licence_number", "TEST-H-001");
        m.put("public_phone", "+15555550101");
        m.put("emergency_phone", "+15555550102");
        m.put("address", "1 Example Road");
        m.put("latitude", "23.2599");
        m.put("longitude", "77.4126");
        return m;
    }

    @Test
    void acceptsGoodRow() {
        assertTrue(InstitutionRowValidator.validate(good()).valid());
    }

    @Test
    void rejectsFormulaInjection() {
        Map<String, String> m = good();
        m.put("name", "=HYPERLINK(\"http://evil\")");
        var result = InstitutionRowValidator.validate(m);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("import.row.name_invalid"));
    }

    @Test
    void rejectsBadTypeAndCoordinates() {
        Map<String, String> m = good();
        m.put("type", "SHOP");
        m.put("latitude", "123");
        var errors = InstitutionRowValidator.validate(m).errors();
        assertTrue(errors.contains("import.row.type_invalid"));
        assertTrue(errors.contains("import.row.latitude_invalid"));
    }

    @Test
    void rejectsBadPhone() {
        Map<String, String> m = good();
        m.put("public_phone", "abc");
        assertTrue(InstitutionRowValidator.validate(m).errors().contains("import.row.public_phone_invalid"));
    }
}
