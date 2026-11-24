package com.raktaveda.donor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BloodGroupTest {

    @Test
    void readsAllEightLabels() {
        for (String label : new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"}) {
            assertThat(BloodGroup.fromLabel(label).label()).isEqualTo(label);
        }
    }

    @Test
    void rejectsAnythingElse() {
        assertThatThrownBy(() -> BloodGroup.fromLabel("C+")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BloodGroup.fromLabel("a+")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BloodGroup.fromLabel(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
