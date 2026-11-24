package com.raktaveda.request;

import com.raktaveda.audit.AuditService;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.SqlUtil;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.donor.BloodComponent;
import com.raktaveda.donor.BloodGroup;
import com.raktaveda.donor.BloodGroupCompatibility;
import com.raktaveda.donor.DonorProfileService;
import com.raktaveda.request.RequestDtos.DonorRequestView;
import com.raktaveda.request.RequestDtos.DonorResponseResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emergency requests from the donor's side: see open requests that this donor can help with,
 * then accept or decline.
 *
 * Privacy: a donor sees the blood group, component, units, urgency and distance rounded up to
 * whole kilometres. Never the requester's identity or place. The donor's phone number is shared
 * with the requester ONLY when the donor accepts. Accepting is the donor's consent.
 * Needs review before real use: contact sharing, and the wording the app shows before "accept".
 *
 * The app never promises that a donor will attend, and never sells blood.
 */
@Service
public class DonorRequestService {

    private static final String ORDER_BY = """
            ORDER BY CASE r.urgency WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                     dist
            LIMIT 50
            """;

    private record DonorState(BloodGroup group, boolean ready) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DonorProfileService profiles;
    private final AuditService audit;
    private final RaktavedaProperties props;

    public DonorRequestService(JdbcTemplate jdbc, TransactionTemplate tx, DonorProfileService profiles,
                               AuditService audit, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.profiles = profiles;
        this.audit = audit;
        this.props = props;
    }

    /** Open requests within 25 km that this donor's blood group can help. Nearest and most urgent first. */
    public List<DonorRequestView> list(UUID donorId) {
        BloodGroup group = requireReadyDonor(donorId);
        return queryVisible(donorId, group, null);
    }

    public DonorResponseResult accept(UUID donorId, UUID requestId) {
        BloodGroup group = requireReadyDonor(donorId);
        // The full check, including the exact age, using the encrypted date of birth.
        if (!profiles.get(donorId).eligibility().eligible()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DONOR_NOT_ELIGIBLE", "error.donor.not_eligible");
        }
        return tx.execute(t -> {
            lockOpenRequest(requestId); // one donor at a time per request, so the limit below is exact
            List<DonorRequestView> visible = queryVisible(donorId, group, requestId);
            if (visible.isEmpty()) {
                throw notFound(); // not open, too far, or not a group match: all look the same
            }
            String existing = visible.get(0).myResponse();
            if ("DONATED".equals(existing)) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DONATED", "error.request.already_donated");
            }
            if ("ACCEPTED".equals(existing)) {
                return new DonorResponseResult(requestId, "ACCEPTED"); // pressing accept twice changes nothing
            }
            Integer accepted = jdbc.queryForObject(
                    "SELECT count(*) FROM request_responses WHERE request_id = ? AND status IN ('ACCEPTED', 'DONATED')",
                    Integer.class, requestId);
            if (accepted != null && accepted >= props.requests().maxAcceptedPerRequest()) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_FULL", "error.request.full");
            }
            UUID responseId = upsert(requestId, donorId, "ACCEPTED");
            audit.record("USER", donorId, "RESPONSE_ACCEPTED", "REQUEST", requestId, null);
            // Needs review before real use (privacy): this is the moment the donor's phone becomes visible
            // to the requester. It is logged without the number.
            audit.record("USER", donorId, "CONTACT_SHARED", "RESPONSE", responseId, null);
            return new DonorResponseResult(requestId, "ACCEPTED");
        });
    }

    public DonorResponseResult decline(UUID donorId, UUID requestId) {
        BloodGroup group = requireReadyDonor(donorId);
        return tx.execute(t -> {
            lockOpenRequest(requestId);
            List<DonorRequestView> visible = queryVisible(donorId, group, requestId);
            if (visible.isEmpty()) {
                throw notFound();
            }
            if ("DONATED".equals(visible.get(0).myResponse())) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DONATED", "error.request.already_donated");
            }
            // Declining after accepting withdraws the donor, and the phone is hidden again.
            UUID responseId = upsert(requestId, donorId, "DECLINED");
            audit.record("USER", donorId, "RESPONSE_DECLINED", "RESPONSE", responseId, null);
            return new DonorResponseResult(requestId, "DECLINED");
        });
    }

    // ---- helpers ----

    /** The donor must have a profile and be "available now" with a location, or distances mean nothing. */
    private BloodGroup requireReadyDonor(UUID donorId) {
        List<DonorState> rows = jdbc.query("""
                SELECT blood_group, (available AND location IS NOT NULL) AS ready
                FROM donor_profiles WHERE user_id = ?
                """,
                (rs, n) -> new DonorState(BloodGroup.fromLabel(rs.getString("blood_group")), rs.getBoolean("ready")),
                donorId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_REQUIRED", "error.profile.required");
        }
        if (!rows.get(0).ready()) {
            throw new ApiException(HttpStatus.CONFLICT, "AVAILABILITY_REQUIRED", "error.donor.availability_required");
        }
        return rows.get(0).group();
    }

    private void lockOpenRequest(UUID requestId) {
        List<Integer> found = jdbc.query(
                "SELECT 1 FROM blood_requests WHERE id = ? AND status = 'OPEN' AND expires_at > now() FOR UPDATE",
                (rs, n) -> rs.getInt(1), requestId);
        if (found.isEmpty()) {
            throw notFound();
        }
    }

    /**
     * Requests this donor may see, computed by PostGIS in one query. A request is visible when it is
     * open, within 25 km of the donor, the donor's group can help, and the donor passes the stored
     * eligibility checks. A donor never sees their own requests.
     */
    private List<DonorRequestView> queryVisible(UUID donorId, BloodGroup group, UUID requestId) {
        List<BloodGroup> redCellRecipients = BloodGroupCompatibility.recipientGroupsFor(BloodComponent.PACKED_RED_CELLS, group);
        List<Object> args = new ArrayList<>();
        args.add(donorId);
        args.add(props.eligibility().minWeightKg());
        args.add(props.eligibility().minDaysBetweenDonations());
        redCellRecipients.forEach(g -> args.add(g.label()));
        args.add(group.label()); // every other component needs the exact same group
        if (requestId != null) {
            args.add(requestId);
        }
        String sql = """
                SELECT r.id, r.blood_group, r.component, r.units, r.urgency, r.needs_transport, r.created_at, r.expires_at,
                       ST_Distance(r.location, dp.location) AS dist, resp.status AS my_status
                FROM donor_profiles dp
                JOIN blood_requests r ON r.status = 'OPEN' AND r.expires_at > now() AND r.location IS NOT NULL
                                     AND ST_DWithin(r.location, dp.location, 25000)
                LEFT JOIN request_responses resp ON resp.request_id = r.id AND resp.donor_user_id = dp.user_id
                WHERE dp.user_id = ? AND dp.available AND dp.location IS NOT NULL
                  AND dp.age_ok AND dp.weight_kg >= ?
                  AND (dp.last_donation_date IS NULL OR dp.last_donation_date <= current_date - (?::int))
                  AND NOT (r.requester_type = 'USER' AND r.requester_id = dp.user_id)
                  AND ((r.component = 'PACKED_RED_CELLS' AND r.blood_group IN (""" + SqlUtil.placeholders(redCellRecipients.size()) + """
                ))
                       OR (r.component <> 'PACKED_RED_CELLS' AND r.blood_group = ?))
                """ + (requestId != null ? "  AND r.id = ?\n" : "") + ORDER_BY;
        return jdbc.query(sql, (rs, n) -> new DonorRequestView(
                rs.getObject("id", UUID.class),
                BloodGroup.fromLabel(rs.getString("blood_group")),
                BloodComponent.valueOf(rs.getString("component")),
                rs.getInt("units"),
                Urgency.valueOf(rs.getString("urgency")),
                rs.getBoolean("needs_transport"),
                // Needs review before real use (privacy): rounded UP to whole km, so several donors
                // cannot easily work out the exact place by comparing distances.
                Math.max(1, (int) Math.ceil(rs.getDouble("dist") / 1000.0)),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("my_status")), args.toArray());
    }

    private UUID upsert(UUID requestId, UUID donorId, String status) {
        List<UUID> ids = jdbc.query("""
                INSERT INTO request_responses (request_id, donor_user_id, status) VALUES (?, ?, ?)
                ON CONFLICT (request_id, donor_user_id)
                DO UPDATE SET status = EXCLUDED.status, responded_at = now()
                WHERE request_responses.status <> 'DONATED'
                RETURNING id
                """, (rs, n) -> rs.getObject(1, UUID.class), requestId, donorId, status);
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DONATED", "error.request.already_donated");
        }
        return ids.get(0);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "error.request.not_found");
    }
}
