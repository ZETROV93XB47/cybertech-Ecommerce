package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.WishlistManagementApiSpec;
import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.services.core.WishlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_WISHLIST;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_WISHLIST_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = USER_WISHLIST_CONTROLLER_BASE_PATH)
@Tag(name = "WishlistController", description = "API for Wishlist management")
public class WishlistManagementController implements WishlistManagementApiSpec {

    private final WishlistService wishlistService;

    @Override
    @PostMapping(value = "/add/{productUuid}", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WishlistResponseDto> addProductToWishlist(@PathVariable UUID productUuid, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.addProductToMyWishlist(jwt.getSubject(), productUuid));
    }

    @Override
    @DeleteMapping(value = "/remove/{productUuid}", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeProductFromWishlist(@PathVariable UUID productUuid, @AuthenticationPrincipal final Jwt jwt) {
        wishlistService.removeProductFromMyWishlist(jwt.getSubject(), productUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/my-wishlist", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<WishlistResponseDto>> getMyWishlist(
            @AuthenticationPrincipal final Jwt jwt,
            @PageableDefault(size = DEFAULT_PAGE_SIZE_WISHLIST, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.ok(wishlistService.getMyWishlist(jwt.getSubject(), pageable));
    }

    // --- ADMIN ENDPOINTS ---

    /*
    @Override
    @GetMapping(value = "/admin/all", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Collection<WishlistResponseDto>> getAllWishlists() {
        return ResponseEntity.ok(wishlistService.getAll());
    }

    @Override
    @DeleteMapping(value = "/admin/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWishlistEntry(@PathVariable UUID uuid, @AuthenticationPrincipal final Jwt jwt) {
        log.info("Current admin user made this api call : {}", jwt.getNotificationSubject());
        wishlistService.deleteByUUID(uuid);
        return ResponseEntity.noContent().build();
    }
     */
}