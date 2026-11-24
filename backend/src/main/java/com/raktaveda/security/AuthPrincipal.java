package com.raktaveda.security;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The logged-in account for one request. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthPrincipal principal}. It holds only an id and a
 * type, never a phone number or email.
 */
public record AuthPrincipal(UUID id, PrincipalType type, String institutionStatus) {

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + type.name()));
        // Only an institution that an admin has verified may act (post stock, requests, ...).
        if (type == PrincipalType.INSTITUTION && "VERIFIED".equals(institutionStatus)) {
            list.add(new SimpleGrantedAuthority("ROLE_INSTITUTION_VERIFIED"));
        }
        return list;
    }
}
