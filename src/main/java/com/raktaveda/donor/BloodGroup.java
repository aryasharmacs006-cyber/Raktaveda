package com.raktaveda.donor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** The eight blood groups. In JSON and in the database they appear as "A+", "O-" and so on. */
public enum BloodGroup {
    A_POS("A+"), A_NEG("A-"), B_POS("B+"), B_NEG("B-"),
    AB_POS("AB+"), AB_NEG("AB-"), O_POS("O+"), O_NEG("O-");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    /** Throws IllegalArgumentException for anything that is not one of the eight labels (Jackson turns that into a 400). */
    @JsonCreator
    public static BloodGroup fromLabel(String text) {
        return Arrays.stream(values())
                .filter(g -> g.label.equals(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown blood group"));
    }
}
