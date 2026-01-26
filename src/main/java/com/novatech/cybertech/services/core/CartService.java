package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;

import java.util.UUID;

public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartUpdateRequestDto, CartResponseDto> {
}
