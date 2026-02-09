package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.spec.BankCardControllerApiSpec;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.services.core.BankCardManagementService;
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

import java.util.Collection;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(BANK_CARD_CRUD_CONTROLLER_BASE_PATH) // Je suppose ce path, à adapter si besoin
@Tag(name = " BankCardManagementController", description = "API for managing Bank Cards")
public class BankCardManagementController implements BankCardControllerApiSpec {

    private final BankCardManagementService bankCardService;

    // --- Endpoints Sécurisés ---

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/add", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> addBankCard(@Valid @RequestBody BankCardCreationRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankCardService.addBankCard(jwt.getSubject(), dto));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @DeleteMapping(value = "/delete", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteBankCard(@AuthenticationPrincipal Jwt jwt) {
        bankCardService.deleteBankCard(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PutMapping(value = "/update", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> updateBankCard(@Valid @RequestBody BankCardUpdateRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(bankCardService.updateBankCard(jwt.getSubject(), dto));
    }

    // --- Endpoints CRUD Basiques (Non sécurisés) ---

    @Override
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Collection<BankCardResponseDto>> getAllBankCards() {
        return ResponseEntity.ok(bankCardService.getAll());
    }

    @Override
    @GetMapping(value = "/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> getBankCardByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(bankCardService.getByUUID(uuid));
    }

    @Override
    @PostMapping(consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> createBankCard(@Valid @RequestBody BankCardCreationRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankCardService.create(dto));
    }

    @Override
    @PutMapping(consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> updateBankCardAdmin(@Valid @RequestBody BankCardUpdateRequestDto dto) {
        return ResponseEntity.ok(bankCardService.update(dto));
    }

    @Override
    @DeleteMapping(value = "/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteBankCardByUuid(@PathVariable UUID uuid) {
        bankCardService.deleteByUUID(uuid);
        return ResponseEntity.noContent().build();
    }
}