package com.raktaveda.donor;

import com.raktaveda.donor.DonorDtos.AvailabilityDto;
import com.raktaveda.donor.DonorDtos.ProfileResponse;
import com.raktaveda.donor.DonorDtos.ProfileUpdateDto;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own donor profile. There is no user id in any URL on purpose. */
@RestController
@RequestMapping("/api/donor")
@PreAuthorize("hasRole('USER')")
public class DonorController {

    private final DonorProfileService profiles;

    public DonorController(DonorProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal AuthPrincipal principal) {
        return profiles.get(principal.id());
    }

    @PutMapping("/profile")
    public ProfileResponse saveProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody ProfileUpdateDto body) {
        return profiles.save(principal.id(), body);
    }

    /** The eligibility estimate is part of the profile response; this is the same data for convenience. */
    @GetMapping("/eligibility")
    public EligibilityService.Result getEligibility(@AuthenticationPrincipal AuthPrincipal principal) {
        return profiles.get(principal.id()).eligibility();
    }

    @PutMapping("/availability")
    public ProfileResponse setAvailability(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody AvailabilityDto body) {
        return profiles.setAvailability(principal.id(), body);
    }
}
