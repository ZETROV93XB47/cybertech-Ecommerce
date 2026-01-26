package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.spec.CartCrudControllerApiSpec;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.implementation.CartServiceImp;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.CART_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(CART_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = " CartController", description = "API for Cart management")
public class CartCrudController implements CartCrudControllerApiSpec {

    private final CartServiceImp cartService;

    @Override
    @GetMapping(value = "/get/{cartUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> getCartByUuid(@PathVariable("cartUuid") UUID cartUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.getByUUID(cartUuid));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> createCart(@Valid @RequestBody CartCreateRequestDto cartCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.create(cartCreateRequestDto));
    }

    @Override
    @PatchMapping(value = "/update/{cartUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CartResponseDto> updateCart(final CartUpdateRequestDto cartUpdateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(cartService.update(cartUpdateRequestDto));
    }

    @Override
    @DeleteMapping(value = "/delete/{cartUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteCartByUuid(UUID cartUuid) {
        cartService.deleteByUUID(cartUuid);
        return ResponseEntity.noContent().build();
    }
}
