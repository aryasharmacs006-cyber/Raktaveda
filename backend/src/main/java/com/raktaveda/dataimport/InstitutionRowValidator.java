package com.raktaveda.dataimport;

import com.raktaveda.common.PhoneNumbers;
import com.raktaveda.institution.InstitutionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks one CSV row. It returns message keys for what is wrong, never the offending text, so an error
 * report cannot leak or repeat what was in the file.
 */
public final class InstitutionRowValidator {

    private static final Pattern LICENCE = Pattern.compile("^[A-Za-z0-9/.\\- ]{3,100}$"); // same rule as registration

    public record ParsedRow(String name, InstitutionType type, String licenceNumber, String publicPhone,
                            String emergencyPhone, String address, double latitude, double longitude) {
    }

    public record Result(ParsedRow row, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private InstitutionRowValidator() {
    }

    /** cells maps a column name (lower case) to its value. */
    public static Result validate(Map<String, String> cells) {
        List<String> errors = new ArrayList<>();

        String name = clean(cells.get("name"));
        if (name.isEmpty() || name.length() > 200 || startsLikeFormula(name)) {
            errors.add("import.row.name_invalid");
        }

        InstitutionType type = null;
        try {
            type = InstitutionType.valueOf(clean(cells.get("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add("import.row.type_invalid");
        }

        String licence = clean(cells.get("licence_number"));
        if (!LICENCE.matcher(licence).matches()) {
            errors.add("import.row.licence_invalid");
        }

        Optional<String> publicPhone = PhoneNumbers.normalize(cells.get("public_phone"));
        if (publicPhone.isEmpty()) {
            errors.add("import.row.public_phone_invalid");
        }
        Optional<String> emergencyPhone = PhoneNumbers.normalize(cells.get("emergency_phone"));
        if (emergencyPhone.isEmpty()) {
            errors.add("import.row.emergency_phone_invalid");
        }

        String address = clean(cells.get("address"));
        if (address.length() > 300 || startsLikeFormula(address)) {
            errors.add("import.row.address_invalid");
        }

        Double latitude = number(cells.get("latitude"), -90, 90);
        if (latitude == null) {
            errors.add("import.row.latitude_invalid");
        }
        Double longitude = number(cells.get("longitude"), -180, 180);
        if (longitude == null) {
            errors.add("import.row.longitude_invalid");
        }

        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors));
        }
        return new Result(new ParsedRow(name, type, licence, publicPhone.get(), emergencyPhone.get(),
                address.isEmpty() ? null : address, latitude, longitude), List.of());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Spreadsheet programs run a cell that starts with = + - or @ as a formula. A listing name or address
     * never needs to start that way, so such rows are refused (this stops "CSV injection").
     */
    private static boolean startsLikeFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private static Double number(String raw, double min, double max) {
        try {
            double v = Double.parseDouble(clean(raw));
            return Double.isFinite(v) && v >= min && v <= max ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
