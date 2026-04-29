package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.CartManagementControllerApiSpec;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.core.CartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.CART_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = CART_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "CartController", description = "API for Cart management")
public class CartManagementController implements CartManagementControllerApiSpec {

    private final CartService cartService;

    //Base CRUD Endpoints, maybe delete these endpoints in the future
    /**
     * BUG-161 — Forwards to the ownership-checked
     * {@link CartService#getByUUID(UUID, String)} overload using the JWT
     * subject as caller identity. Previously {@code getByUUID(UUID)} was
     * called with no caller context, allowing any authenticated user to
     * read any other user's cart by guessing their UUID (IDOR).
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/get/{cartUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> getCartByUuid(@PathVariable("cartUuid") UUID cartUuid, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.getByUUID(cartUuid, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> createCart(@Valid @RequestBody CartCreateRequestDto cartCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.create(cartCreateRequestDto));
    }

    /**
     * BUG-026 / BUG-161 — Update a cart identified by UUID with the
     * correctly-typed {@link CartUpdateRequestDto}. Uses the JWT subject as
     * the caller identity so the service can enforce ownership.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PatchMapping(value = "/update/{cartUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> updateCart(@PathVariable("cartUuid") final UUID cartUuid, @Valid @RequestBody final CartUpdateRequestDto cartUpdateRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.updateCart(cartUuid, cartUpdateRequestDto, jwt.getSubject()));
    }

    /**
     * BUG-161 — Forwards to the ownership-checked
     * {@link CartService#deleteByUUID(UUID, String)} overload. An attacker
     * can no longer delete an unrelated user's cart by guessing its UUID.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @DeleteMapping(value = "/delete/{cartUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteCartByUuid(@PathVariable("cartUuid") UUID cartUuid, @AuthenticationPrincipal final Jwt jwt) {
        cartService.deleteByUUID(cartUuid, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }





    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/get", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> getCart(@AuthenticationPrincipal final Jwt jwt) {
        // FIX(PII-LEAK): removed log of full JWT claims (sub, email, realm_access roles) — only the subject is needed below
        return ResponseEntity.status(HttpStatus.OK).body(cartService.getCart(jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @DeleteMapping(value = "/clear", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal final Jwt jwt) {
        cartService.clearCart(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/add", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> addToCart(@Valid @RequestBody final CartCreateRequestDto cartCreateRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItemsToCart(cartCreateRequestDto, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PatchMapping(value = "/remove/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> removeFromCart(@PathVariable("productUuid") final UUID uuid, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.removeItemFromCart(uuid, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @DeleteMapping(value = "/decreaseQuantity", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> decreaseQuantity(@Valid @RequestBody final CartItemRemoveRequestDto cartItemRemoveRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.decreaseQuantity(cartItemRemoveRequestDto, jwt.getSubject()));
    }
}
