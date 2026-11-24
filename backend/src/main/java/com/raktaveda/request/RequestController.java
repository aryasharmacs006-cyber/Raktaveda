package com.raktaveda.request;

import com.raktaveda.request.RequestDtos.CreateRequestDto;
import com.raktaveda.request.RequestDtos.RequestView;
import com.raktaveda.request.RequestDtos.TransportDto;
import org.springframework.web.bind.annotation.PutMapping;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emergency requests, for the person or institution that posted them. Only users and VERIFIED
 * institutions may use this. An institution waiting for admin approval gets 403.
 */
@RestController
@RequestMapping("/api/requests")
@PreAuthorize("hasAnyRole('USER', 'INSTITUTION_VERIFIED')")
public class RequestController {

    private final RequestService requests;

    public RequestController(RequestService requests) {
        this.requests = requests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView create(@AuthenticationPrincipal AuthPrincipal principal,
                              @Valid @RequestBody CreateRequestDto body) {
        return requests.create(principal, body);
    }

    @GetMapping("/mine")
    public List<RequestView> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return requests.listMine(principal);
    }

    @GetMapping("/{id}")
    public RequestView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return requests.getOwn(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public RequestView cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return requests.cancel(principal, id);
    }

    /** An institution flags that it needs transport arranged through its own channels. Only a flag, no logistics. */
    @PutMapping("/{id}/transport")
    @PreAuthorize("hasRole('INSTITUTION_VERIFIED')")
    public RequestView transport(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                 @Valid @RequestBody TransportDto body) {
        return requests.setTransport(principal, id, body.needsTransport());
    }

    /** Hospital confirms that an accepted donor donated. Starts the donor's cooldown. */
    @PostMapping("/{id}/responses/{responseId}/confirm")
    @PreAuthorize("hasRole('INSTITUTION_VERIFIED')")
    public RequestView confirm(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable UUID id, @PathVariable UUID responseId) {
        return requests.confirmDonation(principal, id, responseId);
    }
}
