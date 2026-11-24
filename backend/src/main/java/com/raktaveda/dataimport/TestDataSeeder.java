package com.raktaveda.dataimport;

import com.raktaveda.auth.UserAuthService;
import com.raktaveda.common.PhoneNumbers;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.dataimport.ImportDtos.ImportReport;
import com.raktaveda.donor.BloodGroup;
import com.raktaveda.donor.DonorDtos.AvailabilityDto;
import com.raktaveda.donor.DonorDtos.ProfileUpdateDto;
import com.raktaveda.donor.DonorProfileService;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TEST DATA ONLY. Loads fictional institutions and three fictional donors so you can try the app.
 * It runs only when RAKTAVEDA_SEED_TEST_DATA=true, and it refuses to run when RAKTAVEDA_REQUIRE_HTTPS=true
 * (the production setting). It is safe to run on every start: existing rows are skipped or refreshed.
 *
 * Everything here is made up. Names say "TEST DATA". Phone numbers use the reserved fictional 555 range
 * (+1 555 555 01xx). The coordinates are ordinary map points near Bhopal, but no real hospital or blood
 * bank is described. Needs review before real use: keep this switched off on a real server.
 *
 * The test donors are switched to "available now". That switches itself off after the configured hours
 * (12 by default) and comes back on at the next start.
 */
@Component
@ConditionalOnProperty(name = "raktaveda.seed.test-data", havingValue = "true")
public class TestDataSeeder implements ApplicationRunner {

    private record TestDonor(String phone, BloodGroup group, double weightKg, double latitude, double longitude) {
    }

    private static final Logger log = LoggerFactory.getLogger(TestDataSeeder.class);

    private static final List<TestDonor> DONORS = List.of(
            new TestDonor("+15555550121", BloodGroup.O_NEG, 68, 23.2610, 77.4130),
            new TestDonor("+15555550122", BloodGroup.A_POS, 72, 23.2450, 77.4100),
            new TestDonor("+15555550123", BloodGroup.B_POS, 61, 23.2800, 77.4300));

    private final RaktavedaProperties props;
    private final InstitutionCsvImportService importer;
    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final DonorProfileService profiles;

    public TestDataSeeder(RaktavedaProperties props, InstitutionCsvImportService importer, JdbcTemplate jdbc,
                          CryptoService crypto, DonorProfileService profiles) {
        this.props = props;
        this.importer = importer;
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.profiles = profiles;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (props.security().requireHttps()) {
            throw new IllegalStateException(
                    "TEST DATA must never be loaded on a real server (RAKTAVEDA_REQUIRE_HTTPS=true). Turn RAKTAVEDA_SEED_TEST_DATA off.");
        }
        byte[] csv;
        try (InputStream in = new ClassPathResource("seed/test-institutions.csv").getInputStream()) {
            csv = in.readAllBytes();
        }
        ImportReport report = importer.run(null, csv, false);
        if (report.invalidRows() > 0) {
            throw new IllegalStateException("The built-in test data file has invalid rows. This is a bug.");
        }
        for (TestDonor donor : DONORS) {
            seedDonor(donor);
        }
        log.warn("TEST DATA loaded: {} new institutions, {} test donors. Do not use on a real server.",
                report.importedRows(), DONORS.size());
    }

    private void seedDonor(TestDonor donor) {
        String phone = PhoneNumbers.normalize(donor.phone()).orElseThrow();
        UUID userId = jdbc.queryForObject("""
                INSERT INTO users (phone_index, phone_enc) VALUES (?, ?)
                ON CONFLICT (phone_index) DO UPDATE SET phone_index = EXCLUDED.phone_index
                RETURNING id
                """, UUID.class, crypto.blindIndex(phone), crypto.encrypt(phone, UserAuthService.PHONE_CONTEXT));
        profiles.save(userId, new ProfileUpdateDto(donor.group(), LocalDate.of(1992, 6, 15), donor.weightKg(), null));
        profiles.setAvailability(userId, new AvailabilityDto(true, donor.latitude(), donor.longitude()));
    }
}
