package com.raktaveda.request;

import com.raktaveda.request.RequestDtos.DonorRequestView;
import com.raktaveda.request.RequestDtos.DonorResponseResult;
import com.raktaveda.security.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open requests as a donor sees them. Details and distance only, never the requester's place. */
@RestController
@RequestMapping("/api/donor/requests")
@PreAuthorize("hasRole('USER')")
public class DonorRequestController {

    private final DonorRequestService donorRequests;

    public DonorRequestController(DonorRequestService donorRequests) {
        this.donorRequests = donorRequests;
    }

    @GetMapping
    public List<DonorRequestView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return donorRequests.list(principal.id());
    }

    /** Accepting shares this donor's phone number with the requester. */
    @PostMapping("/{id}/accept")
    public DonorResponseResult accept(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return donorRequests.accept(principal.id(), id);
    }

    @PostMapping("/{id}/decline")
    public DonorResponseResult decline(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return donorRequests.decline(principal.id(), id);
    }
}
