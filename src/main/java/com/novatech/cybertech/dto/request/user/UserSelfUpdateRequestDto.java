package com.novatech.cybertech.dto.request.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Frontend-gap #3 — user self-service update payload (PATCH /api/v1/services/user/me).
 *
 * <p>Strict subset of {@link UserUpdateRequestDto}: callers can patch their own
 * {@code firstName}, {@code lastName}, {@code phoneNumber} and address (split field-by-field,
 * see below) via this DTO. Email/username are intentionally excluded — those are owned by
 * Keycloak and changing them would invalidate every JWT in flight; admin-only fields
 * ({@code status}, {@code role}) are also off-limits to self-service.</p>
 *
 * <p>The user UUID is NOT carried in the body: the controller resolves the caller from the
 * JWT subject and passes that to the service. Including a UUID here would be an obvious
 * IDOR vector.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSelfUpdateRequestDto {

    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    private String phoneNumber;

    // Split field-by-field (mirrors UserUpdateRequestDto) so a partial update never has to
    // guess the unset components of the address. Each field is null if not modified.
    private String street;
    private String city;
    private String zipCode;
    private String country;
}
