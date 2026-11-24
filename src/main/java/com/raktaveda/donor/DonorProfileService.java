package com.raktaveda.donor;

import com.raktaveda.audit.AuditService;
import com.raktaveda.common.ApiException;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.donor.DonorDtos.AvailabilityDto;
import com.raktaveda.donor.DonorDtos.ProfileResponse;
import com.raktaveda.donor.DonorDtos.ProfileUpdateDto;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * A donor's own profile. Every method takes the logged-in user's id from the token, never
 * from the request, so a user can only ever read or change their own profile.
 */
@Service
public class DonorProfileService {

    /** Names the database column in the encryption, so the value cannot be moved to another column. */
    public static final String DOB_CONTEXT = "donor_profiles.dob";

    private static final int MAX_AGE_YEARS = 120; // only to reject typing mistakes, not an eligibility rule

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final EligibilityService eligibility;
    private final AuditService audit;
    private final RaktavedaProperties props;

    public DonorProfileService(JdbcTemplate jdbc, CryptoService crypto, EligibilityService eligibility,
                               AuditService audit, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.eligibility = eligibility;
        this.audit = audit;
        this.props = props;
    }

    public ProfileResponse get(UUID userId) {
        List<ProfileResponse> rows = jdbc.query("""
                SELECT blood_group, dob_enc, weight_kg, last_donation_date, available,
                       (location IS NOT NULL) AS location_set
                FROM donor_profiles WHERE user_id = ?
                """,
                (rs, n) -> {
                    LocalDate dob = LocalDate.parse(crypto.decrypt(rs.getString("dob_enc"), DOB_CONTEXT));
                    double weight = rs.getBigDecimal("weight_kg").doubleValue();
                    Date last = rs.getDate("last_donation_date");
                    LocalDate lastDonation = last == null ? null : last.toLocalDate();
                    return new ProfileResponse(
                            BloodGroup.fromLabel(rs.getString("blood_group")),
                            dob, weight, lastDonation,
                            rs.getBoolean("available"), rs.getBoolean("location_set"),
                            eligibility.evaluate(dob, weight, lastDonation, LocalDate.now()));
                },
                userId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "error.profile.not_found");
        }
        return rows.get(0);
    }

    /** Creates the profile on first call and replaces its details after that. */
    public ProfileResponse save(UUID userId, ProfileUpdateDto dto) {
        LocalDate today = LocalDate.now();
        // Plain sanity checks. These are NOT eligibility rules; those only produce an estimate.
        if (Period.between(dto.dateOfBirth(), today).getYears() > MAX_AGE_YEARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOB_INVALID", "error.profile.dob_invalid");
        }
        if (dto.lastDonationDate() != null && dto.lastDonationDate().isBefore(dto.dateOfBirth())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_DONATION_INVALID", "error.profile.last_donation_invalid");
        }

        jdbc.update("""
                INSERT INTO donor_profiles (user_id, blood_group, dob_enc, weight_kg, last_donation_date)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    blood_group = EXCLUDED.blood_group,
                    dob_enc = EXCLUDED.dob_enc,
                    weight_kg = EXCLUDED.weight_kg,
                    last_donation_date = EXCLUDED.last_donation_date,
                    updated_at = now()
                """,
                userId, dto.bloodGroup().label(),
                crypto.encrypt(dto.dateOfBirth().toString(), DOB_CONTEXT),
                dto.weightKg(),
                dto.lastDonationDate() == null ? null : Date.valueOf(dto.lastDonationDate()));
        refreshAgeOk(userId);
        // Needs review before real use: a donor can change blood group freely. A wrong group is a
        // patient-safety risk, so the blood bank must always re-test. Every save is audited.
        audit.record("USER", userId, "DONOR_PROFILE_SAVED", "USER", userId, null);
        return get(userId);
    }

    /**
     * Turns "Available now" on or off. On: the location is stored (rounded). Off: the location
     * is erased at once. No history is kept either way, and coordinates are never logged.
     */
    public ProfileResponse setAvailability(UUID userId, AvailabilityDto dto) {
        int changed;
        if (Boolean.TRUE.equals(dto.available())) {
            if (dto.latitude() == null || dto.longitude() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED", "error.availability.location_required");
            }
            // Needs review before real use (privacy): coordinates are rounded to 3 decimals
            // (about 100 m). Matching only needs kilometres, so exact positions are not stored.
            // PostGIS point order is (longitude, latitude).
            changed = jdbc.update("""
                    UPDATE donor_profiles
                    SET available = true,
                        location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        location_updated_at = now(), updated_at = now()
                    WHERE user_id = ?
                    """, round3(dto.longitude()), round3(dto.latitude()), userId);
        } else {
            changed = jdbc.update("""
                    UPDATE donor_profiles
                    SET available = false, location = NULL, location_updated_at = NULL, updated_at = now()
                    WHERE user_id = ?
                    """, userId);
        }
        if (changed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_REQUIRED", "error.profile.required");
        }
        if (Boolean.TRUE.equals(dto.available())) {
            refreshAgeOk(userId); // matching reads this stored answer, so refresh it when the donor goes live
        }
        audit.record("USER", userId, Boolean.TRUE.equals(dto.available()) ? "DONOR_AVAILABLE_ON" : "DONOR_AVAILABLE_OFF",
                "USER", userId, null);
        return get(userId);
    }

    /**
     * Safety net: a donor who forgets to switch off does not stay on the map for ever. After the
     * configured hours, availability is turned off and the location erased.
     */
    @Scheduled(fixedDelay = 600_000)
    public void expireStaleAvailability() {
        jdbc.update("""
                UPDATE donor_profiles
                SET available = false, location = NULL, location_updated_at = NULL
                WHERE available AND location_updated_at < now() - ? * interval '1 hour'
                """, props.donor().availabilityTtlHours());
    }

    /** Works out "is the age allowed" from the encrypted date of birth and stores the yes/no answer. */
    private void refreshAgeOk(UUID userId) {
        String encrypted = jdbc.queryForObject("SELECT dob_enc FROM donor_profiles WHERE user_id = ?", String.class, userId);
        LocalDate dob = LocalDate.parse(crypto.decrypt(encrypted, DOB_CONTEXT));
        jdbc.update("UPDATE donor_profiles SET age_ok = ? WHERE user_id = ?",
                eligibility.ageAllowed(dob, LocalDate.now()), userId);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
