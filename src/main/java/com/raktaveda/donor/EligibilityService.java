package com.raktaveda.donor;

import com.raktaveda.config.RaktavedaProperties;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Works out whether a donor MIGHT be able to donate. This is an estimate only: the blood
 * bank decides, and it also checks haemoglobin. The result carries message keys, never text.
 *
 * Needs review before real use: every rule here comes from configuration and must be
 * verified against current NBTC and MoHFW guidelines before launch.
 */
@Service
public class EligibilityService {

    /** reasons is empty when eligible. estimateKey is always present so the app can show the disclaimer. */
    public record Result(boolean eligible, List<String> reasons, LocalDate nextEligibleDate, String estimateKey) {
    }

    public static final String ESTIMATE_KEY = "eligibility.estimate_only";

    private final RaktavedaProperties.Eligibility rules;

    public EligibilityService(RaktavedaProperties props) {
        this.rules = props.eligibility();
    }

    /** Is the age inside the allowed range? Used to fill the age_ok column that matching queries read. */
    public boolean ageAllowed(LocalDate dateOfBirth, LocalDate today) {
        int age = Period.between(dateOfBirth, today).getYears();
        return age >= rules.minAgeYears() && age <= rules.maxAgeYears();
    }

    public Result evaluate(LocalDate dateOfBirth, double weightKg, LocalDate lastDonation, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        LocalDate next = null;

        int age = Period.between(dateOfBirth, today).getYears(); // whole years completed
        if (age < rules.minAgeYears()) {
            reasons.add("eligibility.age.too_young");
        } else if (age > rules.maxAgeYears()) {
            // Needs review before real use: "65" is treated as the last allowed age, inclusive.
            reasons.add("eligibility.age.too_old");
        }

        if (weightKg < rules.minWeightKg()) {
            reasons.add("eligibility.weight.too_low");
        }

        if (lastDonation != null) {
            long days = ChronoUnit.DAYS.between(lastDonation, today);
            if (days < rules.minDaysBetweenDonations()) {
                reasons.add("eligibility.cooldown.active");
                next = lastDonation.plusDays(rules.minDaysBetweenDonations());
            }
        }
        return new Result(reasons.isEmpty(), List.copyOf(reasons), next, ESTIMATE_KEY);
    }
}
