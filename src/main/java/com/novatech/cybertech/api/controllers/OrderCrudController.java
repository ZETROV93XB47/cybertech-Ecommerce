package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.spec.OrderCrudControllerApiSpec;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(ORDER_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = " OrderController", description = "API for Cart management")
public class OrderCrudController implements OrderCrudControllerApiSpec {

    private final OrderManagementServiceImp orderService;

    @Override
    @GetMapping(value = "/get/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> getOrderByUuid(@PathVariable("uuid") UUID orderUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(orderService.getByUUID(orderUuid));
    }

    @Override
    @DeleteMapping(value = "/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteOrderByUuid(@PathVariable @Valid final UUID uuid) {
        orderService.deleteByUUID(uuid);
        return ResponseEntity.noContent().build();
    }
}
