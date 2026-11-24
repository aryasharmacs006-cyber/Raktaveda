package com.raktaveda.notification;

import com.raktaveda.notification.NotificationDtos.DeviceTokenDto;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A donor's phone registers its push token here so it can receive alerts about new requests.
 * The token is sent in the body (not the URL) so it does not end up in server logs.
 */
@RestController
@RequestMapping("/api/donor/device-token")
@PreAuthorize("hasRole('USER')")
public class DeviceTokenController {

    private final NotificationService notifications;

    public DeviceTokenController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeviceTokenDto body) {
        notifications.registerToken(principal.id(), body.token());
    }

    @PostMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeviceTokenDto body) {
        notifications.unregisterToken(principal.id(), body.token());
    }
}
