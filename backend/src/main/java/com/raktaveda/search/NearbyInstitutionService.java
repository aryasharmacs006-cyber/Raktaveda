package com.raktaveda.search;

import com.raktaveda.common.ApiException;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.institution.InstitutionRegistrationService;
import com.raktaveda.search.SearchDtos.NearbyInstitution;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Finds VERIFIED hospitals and blood banks near a point, nearest first, so the frontend can draw them on a map.
 *
 * This is a read-only list. It touches no donor, request, chat or stock data. It shows only what an institution
 * made public: name, type, address, public phone and its own location (an institution's place, not a person's).
 * The point the caller sends is used for this one search and is not stored or logged.
 *
 * Needs review before real use (privacy): the public phone is shown to every logged-in user.
 * Imported institutions (source IMPORTED) are included. With test data on, these are made-up places.
 */
@Service
public class NearbyInstitutionService {

    static final double DEFAULT_RADIUS_KM = 10;
    static final double MAX_RADIUS_KM = 50;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    private static final Set<String> TYPES = Set.of("HOSPITAL", "BLOOD_BANK");

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;

    public NearbyInstitutionService(JdbcTemplate jdbc, CryptoService crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    /** All inputs are optional strings from the URL, so a bad value gives our own error code, not a stack trace. */
    public List<NearbyInstitution> find(Double lat, Double lon, Double radiusKm, String type, Integer limit) {
        Query q = validate(lat, lon, radiusKm, type, limit);

        StringBuilder sql = new StringBuilder("""
                SELECT id, name, type, address, public_phone_enc,
                       ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon,
                       ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS dist
                FROM institutions
                WHERE status = 'VERIFIED'
                  AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """);
        List<Object> args = new ArrayList<>(List.of(q.lon, q.lat, q.lon, q.lat, q.radiusKm * 1000));
        if (q.type != null) {
            sql.append(" AND type = ?");
            args.add(q.type);
        }
        sql.append(" ORDER BY dist LIMIT ?");
        args.add(q.limit);

        return jdbc.query(sql.toString(), (rs, n) -> new NearbyInstitution(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("address"),
                crypto.decrypt(rs.getString("public_phone_enc"), InstitutionRegistrationService.PUBLIC_PHONE_CONTEXT),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                Math.round(rs.getDouble("dist") / 100.0) / 10.0), args.toArray());
    }

    record Query(double lat, double lon, double radiusKm, String type, int limit) {
    }

    /** Checks the inputs and fills in the defaults. Package-visible so it can be tested without a database. */
    static Query validate(Double lat, Double lon, Double radiusKm, String type, Integer limit) {
        if (lat == null || lon == null || lat.isNaN() || lon.isNaN() || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INVALID", "error.search.location_invalid");
        }
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (Double.isNaN(radius) || radius <= 0 || radius > MAX_RADIUS_KM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RADIUS_INVALID", "error.search.radius_invalid");
        }
        String cleanType = null;
        if (type != null && !type.isBlank()) {
            cleanType = type.trim().toUpperCase(java.util.Locale.ROOT);
            if (!TYPES.contains(cleanType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_INVALID", "error.search.type_invalid");
            }
        }
        int max = limit == null ? DEFAULT_LIMIT : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
        }
        return new Query(lat, lon, radius, cleanType, max);
    }
}
