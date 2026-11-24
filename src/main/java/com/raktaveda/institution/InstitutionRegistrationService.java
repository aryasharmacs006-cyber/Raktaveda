package com.raktaveda.institution;

import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.AuthDtos.InstitutionRegisterDto;
import com.raktaveda.auth.AuthDtos.InstitutionRegisterResponse;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.PhoneNumbers;
import com.raktaveda.crypto.CryptoService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Registers a hospital or blood bank. It starts as PENDING; an admin must verify it before it can act. */
@Service
public class InstitutionRegistrationService {

    public static final String PUBLIC_PHONE_CONTEXT = "institutions.public_phone";
    public static final String EMERGENCY_PHONE_CONTEXT = "institutions.emergency_phone";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final CryptoService crypto;
    private final AuditService audit;

    public InstitutionRegistrationService(JdbcTemplate jdbc, PasswordEncoder encoder, CryptoService crypto, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.crypto = crypto;
        this.audit = audit;
    }

    public InstitutionRegisterResponse register(InstitutionRegisterDto dto) {
        String publicPhone = PhoneNumbers.normalize(dto.publicPhone())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "error.phone.invalid"));
        String emergencyPhone = PhoneNumbers.normalize(dto.emergencyPhone())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "error.phone.invalid"));
        if (dto.password().getBytes(StandardCharsets.UTF_8).length > 72) { // BCrypt reads at most 72 bytes
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
        }
        try {
            // PostGIS point order is (longitude, latitude).
            UUID id = jdbc.queryForObject("""
                    INSERT INTO institutions (name, type, licence_number, email, password_hash,
                                              public_phone_enc, emergency_phone_enc, address, location)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                    RETURNING id
                    """, UUID.class,
                    dto.name().trim(), dto.type().name(), dto.licenceNumber().trim(),
                    dto.email().trim().toLowerCase(Locale.ROOT), encoder.encode(dto.password()),
                    crypto.encrypt(publicPhone, PUBLIC_PHONE_CONTEXT),
                    crypto.encrypt(emergencyPhone, EMERGENCY_PHONE_CONTEXT),
                    dto.address(), dto.longitude(), dto.latitude());
            audit.record("ANONYMOUS", null, "INSTITUTION_REGISTERED", "INSTITUTION", id, dto.type().name());
            return new InstitutionRegisterResponse(id, "PENDING");
        } catch (DuplicateKeyException e) {
            // Needs review before real use: this tells a caller that an email or licence is taken.
            throw new ApiException(HttpStatus.CONFLICT, "INSTITUTION_EXISTS", "error.institution.exists");
        }
    }
}
