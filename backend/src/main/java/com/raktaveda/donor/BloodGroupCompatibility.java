package com.raktaveda.donor;

import java.util.Arrays;
import java.util.List;

/**
 * Which donor blood groups can help which patients.
 *
 * Needs review before real use (medical rule): please have a doctor or blood bank check this.
 * - PACKED_RED_CELLS follow the standard ABO and Rh red cell rules: O- can give to everyone and
 *   AB+ can receive from everyone.
 * - Every other component (whole blood, platelets, plasma) is matched on the EXACT same group.
 *   This is the cautious choice. Plasma and platelets follow different rules from red cells, and
 *   whole blood is normally given group for group.
 *
 * Terms: ABO is the letter part (A, B, AB, O). Rh is the plus or minus sign.
 */
public final class BloodGroupCompatibility {

    private BloodGroupCompatibility() {
    }

    /** Which components use the red cell rules. Only these show donor counts. */
    public static boolean usesRedCellRules(BloodComponent component) {
        return component == BloodComponent.PACKED_RED_CELLS;
    }

    /** Can this donor's red cells go to this patient? */
    static boolean redCellsCompatible(BloodGroup donor, BloodGroup recipient) {
        String donorAbo = abo(donor);
        String recipientAbo = abo(recipient);
        boolean aboOk = donorAbo.equals("O") || recipientAbo.equals("AB") || donorAbo.equals(recipientAbo);
        boolean rhOk = isPositive(recipient) || !isPositive(donor); // an Rh+ donor can only help an Rh+ patient
        return aboOk && rhOk;
    }

    /** Donor groups that may give to this patient. */
    public static List<BloodGroup> donorGroupsFor(BloodComponent component, BloodGroup recipient) {
        if (!usesRedCellRules(component)) {
            return List.of(recipient);
        }
        return Arrays.stream(BloodGroup.values()).filter(d -> redCellsCompatible(d, recipient)).toList();
    }

    /** Patient groups that this donor may give to. */
    public static List<BloodGroup> recipientGroupsFor(BloodComponent component, BloodGroup donor) {
        if (!usesRedCellRules(component)) {
            return List.of(donor);
        }
        return Arrays.stream(BloodGroup.values()).filter(r -> redCellsCompatible(donor, r)).toList();
    }

    private static String abo(BloodGroup group) {
        String label = group.label();
        return label.substring(0, label.length() - 1);
    }

    private static boolean isPositive(BloodGroup group) {
        return group.label().endsWith("+");
    }
}
