package com.raktaveda.search;

import com.raktaveda.search.SearchDtos.NearbyInstitution;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Map data for the frontend. The path starts with /api/search, so the "search" rate limit (60 per minute) applies.
 * Example: GET /api/search/institutions/nearby?lat=23.2599&lon=77.4126&radiusKm=10&type=BLOOD_BANK
 */
@RestController
@RequestMapping("/api/search")
@PreAuthorize("hasAnyRole('USER','INSTITUTION')")
public class NearbyInstitutionController {

    private final NearbyInstitutionService service;

    public NearbyInstitutionController(NearbyInstitutionService service) {
        this.service = service;
    }

    @GetMapping("/institutions/nearby")
    public List<NearbyInstitution> nearby(@RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lon,
                                          @RequestParam(required = false) Double radiusKm,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) Integer limit) {
        return service.find(lat, lon, radiusKm, type, limit);
    }
}
