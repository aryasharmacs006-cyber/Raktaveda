package com.raktaveda.request;

import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.UserAuthService;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.SqlUtil;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.donor.BloodComponent;
import com.raktaveda.donor.BloodGroup;
import com.raktaveda.donor.BloodGroupCompatibility;
import com.raktaveda.notification.NotificationService;
import com.raktaveda.request.RequestDtos.CreateRequestDto;
import com.raktaveda.request.RequestDtos.DonorCounts;
import com.raktaveda.request.RequestDtos.RequestView;
import com.raktaveda.request.RequestDtos.ResponseView;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.AuthPrincipal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emergency requests from the requester's side. A requester (a user or a verified institution)
 * can only ever see and change their OWN requests: every query filters on the id from the token.
 */
@Service
public class RequestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestService.class);
    private static final Duration CREATE_WINDOW = Duration.ofHours(1);
    private static final String MASKED_CONTACT = "**********";

    private record Row(UUID id, BloodGroup bloodGroup, BloodComponent component, int units, Urgency urgency,
                       String status, boolean needsTransport, java.time.Instant createdAt, java.time.Instant expiresAt) {
    }

    private static final RowMapper<Row> ROW_MAPPER = (rs, n) -> new Row(
            rs.getObject("id", UUID.class),
            BloodGroup.fromLabel(rs.getString("blood_group")),
            BloodComponent.valueOf(rs.getString("component")),
            rs.getInt("units"),
            Urgency.valueOf(rs.getString("urgency")),
            rs.getString("status"),
            rs.getBoolean("needs_transport"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant());

    private static final String SELECT_COLUMNS =
            "SELECT id, blood_group, component, units, urgency, status, needs_transport, created_at, expires_at FROM blood_requests ";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final RaktavedaProperties props;
    private final NotificationService notifications;

    public RequestService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, AuditService audit,
                          RateLimiter limiter, RaktavedaProperties props, NotificationService notifications) {
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
    }

    public RequestView create(AuthPrincipal principal, CreateRequestDto dto) {
        String requesterType = principal.type().name();
        boolean hasLat = dto.latitude() != null;
        boolean hasLon = dto.longitude() != null;
        if (hasLat != hasLon) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INVALID", "error.request.location_invalid");
        }
        if (!hasLat && principal.type() != com.raktaveda.security.PrincipalType.INSTITUTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED", "error.request.location_required");
        }
        if (!limiter.tryAcquire("req-create:" + principal.id(), props.requests().createPerHour(), CREATE_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "REQUEST_RATE_LIMITED", "error.request.rate_limited");
        }

        UUID id = tx.execute(t -> {
            // Makes two requests from the same requester wait for each other, so the open-request limit is exact.
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", (RowCallbackHandler) rs -> { }, "req-open:" + principal.id());
            Integer open = jdbc.queryForObject(
                    "SELECT count(*) FROM blood_requests WHERE requester_type = ? AND requester_id = ? AND status = 'OPEN'",
                    Integer.class, requesterType, principal.id());
            if (open != null && open >= props.requests().maxOpenPerRequester()) {
                throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_OPEN_REQUESTS", "error.request.too_many_open");
            }
            if (hasLat) {
                // Needs review before real use (privacy): the place is rounded to about 100 m and is erased
                // when the request ends. Donors never see it. PostGIS point order is (longitude, latitude).
                return jdbc.queryForObject("""
                        INSERT INTO blood_requests (requester_type, requester_id, blood_group, component, units, urgency,
                                                    location, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, now() + ? * interval '1 hour')
                        RETURNING id
                        """, UUID.class,
                        requesterType, principal.id(), dto.bloodGroup().label(), dto.component().name(), dto.units(),
                        dto.urgency().name(), round3(dto.longitude()), round3(dto.latitude()), props.requests().expiryHours());
            }
            // An institution that gave no place uses its own registered location.
            return jdbc.queryForObject("""
                    INSERT INTO blood_requests (requester_type, requester_id, blood_group, component, units, urgency,
                                                location, expires_at)
                    SELECT ?, i.id, ?, ?, ?, ?, i.location, now() + ? * interval '1 hour'
                    FROM institutions i WHERE i.id = ?
                    RETURNING id
                    """, UUID.class,
                    requesterType, dto.bloodGroup().label(), dto.component().name(), dto.units(),
                    dto.urgency().name(), props.requests().expiryHours(), principal.id());
        });
        audit.record(requesterType, principal.id(), "REQUEST_CREATED", "REQUEST", id, dto.urgency().name());
        try {
            notifications.notifyNewRequest(id);
        } catch (RuntimeException e) {
            // The request is already saved. A failed alert must never undo it. Log the type only.
            log.warn("Notifying donors failed type={}", e.getClass().getSimpleName());
        }
        return getOwn(principal, id);
    }

    public List<RequestView> listMine(AuthPrincipal principal) {
        List<Row> rows = jdbc.query(SELECT_COLUMNS
                        + "WHERE requester_type = ? AND requester_id = ? ORDER BY created_at DESC LIMIT 50",
                ROW_MAPPER, principal.type().name(), principal.id());
        return rows.stream().map(r -> toView(r, false)).toList();
    }

    public RequestView getOwn(AuthPrincipal principal, UUID requestId) {
        List<Row> rows = jdbc.query(SELECT_COLUMNS + "WHERE id = ? AND requester_type = ? AND requester_id = ?",
                ROW_MAPPER, requestId, principal.type().name(), principal.id());
        if (rows.isEmpty()) {
            throw notFound(); // same answer for "not yours" and "does not exist"
        }
        return toView(rows.get(0), true);
    }

    public RequestView cancel(AuthPrincipal principal, UUID requestId) {
        int changed = jdbc.update("""
                UPDATE blood_requests SET status = 'CANCELLED', closed_at = now(), location = NULL
                WHERE id = ? AND requester_type = ? AND requester_id = ? AND status = 'OPEN'
                """, requestId, principal.type().name(), principal.id());
        if (changed == 0) {
            getOwn(principal, requestId); // throws 404 if it is not theirs
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "error.request.closed");
        }
        audit.record(principal.type().name(), principal.id(), "REQUEST_CANCELLED", "REQUEST", requestId, null);
        return getOwn(principal, requestId);
    }

    /**
     * A verified institution confirms that an accepted donor really donated.
     * Needs review before real use (medical and privacy rule): only the institution that
     * posted the request can confirm. Confirming starts the donor's cooldown, switches the
     * donor's "available now" off and erases the location. The request closes once as many
     * donations are confirmed as units were asked for.
     */
    public RequestView confirmDonation(AuthPrincipal principal, UUID requestId, UUID responseId) {
        tx.executeWithoutResult(t -> {
            List<Object[]> locked = jdbc.query("""
                    SELECT units, status, (expires_at > now()) AS live FROM blood_requests
                    WHERE id = ? AND requester_type = 'INSTITUTION' AND requester_id = ? FOR UPDATE
                    """, (rs, n) -> new Object[]{rs.getInt("units"), rs.getString("status"), rs.getBoolean("live")},
                    requestId, principal.id());
            if (locked.isEmpty()) {
                throw notFound();
            }
            int units = (Integer) locked.get(0)[0];
            if (!"OPEN".equals(locked.get(0)[1]) || !(Boolean) locked.get(0)[2]) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "error.request.closed");
            }
            List<UUID> donors = jdbc.query("""
                    UPDATE request_responses SET status = 'DONATED', responded_at = now()
                    WHERE id = ? AND request_id = ? AND status = 'ACCEPTED'
                    RETURNING donor_user_id
                    """, (rs, n) -> rs.getObject(1, UUID.class), responseId, requestId);
            if (donors.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "RESPONSE_NOT_ACCEPTED", "error.request.response_not_accepted");
            }
            UUID donorId = donors.get(0);
            // Cooldown starts today. The donor goes offline and the stored location is erased.
            jdbc.update("""
                    UPDATE donor_profiles
                    SET last_donation_date = current_date, available = false, location = NULL,
                        location_updated_at = NULL, updated_at = now()
                    WHERE user_id = ?
                    """, donorId);
            Integer donated = jdbc.queryForObject(
                    "SELECT count(*) FROM request_responses WHERE request_id = ? AND status = 'DONATED'",
                    Integer.class, requestId);
            if (donated != null && donated >= units) {
                jdbc.update("UPDATE blood_requests SET status = 'CLOSED', closed_at = now(), location = NULL WHERE id = ?",
                        requestId);
            }
            audit.record("INSTITUTION", principal.id(), "DONATION_CONFIRMED", "RESPONSE", responseId, null);
            audit.record("INSTITUTION", principal.id(), "COOLDOWN_STARTED", "USER", donorId, null);
        });
        return getOwn(principal, requestId);
    }

    /**
     * A verified institution marks that it needs transport arranged through its own channels.
     * This is only a flag that donors can see. Raktaveda does no delivery logistics.
     */
    public RequestView setTransport(AuthPrincipal principal, UUID requestId, boolean needsTransport) {
        int changed = jdbc.update("""
                UPDATE blood_requests SET needs_transport = ?
                WHERE id = ? AND requester_type = 'INSTITUTION' AND requester_id = ? AND status = 'OPEN'
                """, needsTransport, requestId, principal.id());
        if (changed == 0) {
            getOwn(principal, requestId); // throws 404 if it is not theirs
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "error.request.closed");
        }
        audit.record("INSTITUTION", principal.id(), "TRANSPORT_FLAG_CHANGED", "REQUEST", requestId,
                needsTransport ? "ON" : "OFF");
        return getOwn(principal, requestId);
    }

    /** Ends requests that ran out of time (erasing their location) and deletes old finished ones. */
    @Scheduled(fixedDelay = 600_000)
    public void expireAndPurge() {
        jdbc.update("UPDATE blood_requests SET status = 'EXPIRED', closed_at = now(), location = NULL "
                + "WHERE status = 'OPEN' AND expires_at < now()");
        jdbc.update("DELETE FROM blood_requests WHERE status <> 'OPEN' AND closed_at < now() - ? * interval '1 day'",
                props.requests().retentionDays());
    }

    // ---- helpers ----

    private RequestView toView(Row row, boolean detailed) {
        DonorCounts counts = null;
        List<ResponseView> responses = null;
        if (detailed) {
            boolean open = "OPEN".equals(row.status());
            if (open && BloodGroupCompatibility.usesRedCellRules(row.component())) {
                counts = countDonors(row.id(), row.bloodGroup(), row.component());
            }
            responses = loadResponses(row.id(), open);
        }
        return new RequestView(row.id(), row.bloodGroup(), row.component(), row.units(), row.urgency(),
                row.status(), row.needsTransport(), row.createdAt(), row.expiresAt(), counts, responses);
    }

    /**
     * Counts donors inside 5, 10 and 25 km of the request, all in one PostGIS query.
     * A donor counts only if: available now with a location, compatible group, age allowed,
     * weight allowed and the cooldown is over. The requester is never counted as their own donor.
     */
    private DonorCounts countDonors(UUID requestId, BloodGroup recipient, BloodComponent component) {
        List<BloodGroup> donorGroups = BloodGroupCompatibility.donorGroupsFor(component, recipient);
        List<Object> args = new ArrayList<>();
        args.add(requestId);
        donorGroups.forEach(g -> args.add(g.label()));
        args.add(props.eligibility().minWeightKg());
        args.add(props.eligibility().minDaysBetweenDonations());
        String sql = """
                SELECT count(*) FILTER (WHERE ST_Distance(dp.location, r.location) <= 5000)  AS c5,
                       count(*) FILTER (WHERE ST_Distance(dp.location, r.location) <= 10000) AS c10,
                       count(*)                                                                AS c25
                FROM blood_requests r
                JOIN donor_profiles dp ON dp.available AND dp.location IS NOT NULL
                                      AND ST_DWithin(dp.location, r.location, 25000)
                WHERE r.id = ? AND r.location IS NOT NULL
                  AND dp.blood_group IN (""" + SqlUtil.placeholders(donorGroups.size()) + """
                )
                  AND dp.age_ok AND dp.weight_kg >= ?
                  AND (dp.last_donation_date IS NULL OR dp.last_donation_date <= current_date - (?::int))
                  AND NOT (r.requester_type = 'USER' AND r.requester_id = dp.user_id)
                """;
        return jdbc.queryForObject(sql,
                (rs, n) -> new DonorCounts(rs.getInt("c5"), rs.getInt("c10"), rs.getInt("c25")),
                args.toArray());
    }

    /**
     * Donors who accepted. Their phone is revealed here and ONLY here, and only to the owner of an
     * OPEN request. Once the request ends the phone is hidden again. Donors who declined are not listed.
     */
    private List<ResponseView> loadResponses(UUID requestId, boolean revealPhones) {
        int[] number = {0};
        return jdbc.query("""
                SELECT resp.id, resp.status, resp.responded_at, dp.blood_group, u.phone_enc
                FROM request_responses resp
                JOIN users u ON u.id = resp.donor_user_id
                JOIN donor_profiles dp ON dp.user_id = resp.donor_user_id
                WHERE resp.request_id = ? AND resp.status IN ('ACCEPTED', 'DONATED')
                ORDER BY resp.responded_at
                """, (rs, n) -> {
                    String phone = revealPhones
                            ? crypto.decrypt(rs.getString("phone_enc"), UserAuthService.PHONE_CONTEXT)
                            : null;
                    return new ResponseView(rs.getObject("id", UUID.class), ++number[0],
                            BloodGroup.fromLabel(rs.getString("blood_group")), rs.getString("status"),
                            MASKED_CONTACT, phone, rs.getTimestamp("responded_at").toInstant());
                }, requestId);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "error.request.not_found");
    }
}
