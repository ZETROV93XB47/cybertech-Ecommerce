package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

//TODO: à refactorer plus tard
public interface ProductManagementService extends CrudBaseService<UUID, ProductCreateRequestDto, ProductUpdateRequestDto, ProductResponseDto> {
    @Transactional
    ProductResponseDto createWithImage(ProductCreateRequestDto productCreateRequestDto, MultipartFile image);
}