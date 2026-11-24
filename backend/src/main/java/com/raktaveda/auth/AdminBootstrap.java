package com.raktaveda.auth;

import com.raktaveda.audit.AuditService;
import com.raktaveda.config.RaktavedaProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the admin account at startup from RAKTAVEDA_ADMIN_EMAIL and RAKTAVEDA_ADMIN_PASSWORD.
 * There is no public API for creating admins. If an admin with that email already exists,
 * nothing changes (the password in the environment is NOT re-applied).
 * To reset a lost admin password, delete the row from the admins table and restart.
 * Needs review before real use: admin recovery process.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final RaktavedaProperties props;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminBootstrap(RaktavedaProperties props, JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        this.props = props;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.admin().email();
        String password = props.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admin created: RAKTAVEDA_ADMIN_EMAIL and RAKTAVEDA_ADMIN_PASSWORD are not both set.");
            return;
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 12 || bytes > 72) {
            throw new IllegalStateException("RAKTAVEDA_ADMIN_PASSWORD must be 12 to 72 characters.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM admins WHERE lower(email) = ?", Integer.class, normalized);
        if (existing != null && existing > 0) {
            log.info("Admin account already exists; environment password not re-applied.");
            return;
        }
        jdbc.update("INSERT INTO admins (email, password_hash) VALUES (?, ?)", normalized, encoder.encode(password));
        audit.record("SYSTEM", null, "ADMIN_BOOTSTRAPPED", "ADMIN", null, null);
        log.info("Admin account created from environment settings.");
    }
}
