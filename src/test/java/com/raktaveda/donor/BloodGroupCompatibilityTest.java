package com.raktaveda.donor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BloodGroupCompatibilityTest {

    private static final BloodComponent RED = BloodComponent.PACKED_RED_CELLS;

    @Test
    void oNegativeGivesRedCellsToEveryone() {
        assertThat(BloodGroupCompatibility.recipientGroupsFor(RED, BloodGroup.O_NEG))
                .containsExactlyInAnyOrder(BloodGroup.values());
    }

    @Test
    void abPositiveReceivesRedCellsFromEveryone() {
        assertThat(BloodGroupCompatibility.donorGroupsFor(RED, BloodGroup.AB_POS))
                .containsExactlyInAnyOrder(BloodGroup.values());
    }

    @Test
    void aPositivePatientCanGetFromFourGroups() {
        assertThat(BloodGroupCompatibility.donorGroupsFor(RED, BloodGroup.A_POS))
                .containsExactlyInAnyOrder(BloodGroup.A_POS, BloodGroup.A_NEG, BloodGroup.O_POS, BloodGroup.O_NEG);
    }

    @Test
    void negativePatientsNeverGetPositiveBlood() {
        for (BloodGroup recipient : List.of(BloodGroup.A_NEG, BloodGroup.B_NEG, BloodGroup.AB_NEG, BloodGroup.O_NEG)) {
            assertThat(BloodGroupCompatibility.donorGroupsFor(RED, recipient))
                    .allMatch(g -> g.label().endsWith("-"));
        }
    }

    @Test
    void abPositiveDonorCanOnlyHelpAbPositive() {
        assertThat(BloodGroupCompatibility.recipientGroupsFor(RED, BloodGroup.AB_POS))
                .containsExactly(BloodGroup.AB_POS);
    }

    @Test
    void donorAndRecipientListsAgreeForEveryPair() {
        for (BloodGroup donor : BloodGroup.values()) {
            for (BloodGroup recipient : BloodGroup.values()) {
                assertThat(BloodGroupCompatibility.recipientGroupsFor(RED, donor).contains(recipient))
                        .isEqualTo(BloodGroupCompatibility.donorGroupsFor(RED, recipient).contains(donor));
            }
        }
    }

    @Test
    void otherComponentsNeedTheExactSameGroup() {
        for (BloodComponent component : Arrays.asList(
                BloodComponent.WHOLE_BLOOD, BloodComponent.PLATELETS, BloodComponent.PLASMA)) {
            assertThat(BloodGroupCompatibility.donorGroupsFor(component, BloodGroup.B_POS)).containsExactly(BloodGroup.B_POS);
            assertThat(BloodGroupCompatibility.recipientGroupsFor(component, BloodGroup.O_NEG)).containsExactly(BloodGroup.O_NEG);
        }
    }
}
