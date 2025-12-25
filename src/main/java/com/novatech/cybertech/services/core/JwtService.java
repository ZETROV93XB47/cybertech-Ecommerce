package com.novatech.cybertech.services.core;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    String generateToken(final UserDetails userDetails);

    String extractUsername(final String token);

    boolean isTokenValid(final String token, final UserDetails userDetails);

}
