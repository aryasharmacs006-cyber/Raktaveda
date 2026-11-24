package com.raktaveda.donor;

import static org.assertj.core.api.Assertions.assertThat;

import com.raktaveda.config.RaktavedaProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EligibilityServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private final EligibilityService service = new EligibilityService(new RaktavedaProperties(
            null, null, null, null, null,
            new RaktavedaProperties.Eligibility(18, 65, 45, 120), null, null));

    @Test
    void healthyAdultIsEligible() {
        var result = service.evaluate(LocalDate.of(1995, 3, 10), 60, null, TODAY);
        assertThat(result.eligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.estimateKey()).isEqualTo("eligibility.estimate_only");
    }

    @Test
    void ageBoundariesAreInclusive() {
        assertThat(service.evaluate(TODAY.minusYears(18), 60, null, TODAY).eligible()).isTrue();
        assertThat(service.evaluate(TODAY.minusYears(18).plusDays(1), 60, null, TODAY).reasons())
                .containsExactly("eligibility.age.too_young");
        assertThat(service.evaluate(TODAY.minusYears(65), 60, null, TODAY).eligible()).isTrue();
        assertThat(service.evaluate(TODAY.minusYears(66), 60, null, TODAY).reasons())
                .containsExactly("eligibility.age.too_old");
    }

    @Test
    void lowWeightIsNotEligible() {
        assertThat(service.evaluate(LocalDate.of(1995, 3, 10), 44.9, null, TODAY).reasons())
                .containsExactly("eligibility.weight.too_low");
        assertThat(service.evaluate(LocalDate.of(1995, 3, 10), 45.0, null, TODAY).eligible()).isTrue();
    }

    @Test
    void cooldownBlocksUntilTheConfiguredDays() {
        LocalDate dob = LocalDate.of(1995, 3, 10);
        var recent = service.evaluate(dob, 60, TODAY.minusDays(119), TODAY);
        assertThat(recent.reasons()).containsExactly("eligibility.cooldown.active");
        assertThat(recent.nextEligibleDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(service.evaluate(dob, 60, TODAY.minusDays(120), TODAY).eligible()).isTrue();
    }

    @Test
    void severalReasonsAreAllReported() {
        var result = service.evaluate(TODAY.minusYears(16), 40, TODAY.minusDays(10), TODAY);
        assertThat(result.reasons()).containsExactly(
                "eligibility.age.too_young", "eligibility.weight.too_low", "eligibility.cooldown.active");
    }
}
