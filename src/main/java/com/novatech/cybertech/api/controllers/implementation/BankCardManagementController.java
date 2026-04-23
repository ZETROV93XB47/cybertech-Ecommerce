package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.BankCardControllerApiSpec;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.services.core.BankCardManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_CRUD_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_BANK_CARD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = BANK_CARD_CRUD_CONTROLLER_BASE_PATH)
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

    /**
     * BUG-038: marks the supplied card as the caller's default. The JWT subject drives
     * ownership and sibling lookup at the service layer.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PatchMapping(value = "/set-default/{cardUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setDefaultBankCard(@PathVariable UUID cardUuid, @AuthenticationPrincipal Jwt jwt) {
        bankCardService.setDefault(cardUuid, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * BUG-038: returns the caller's default card as a masked response DTO.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/default", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> getDefaultBankCard(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(bankCardService.getDefaultCard(jwt.getSubject()));
    }

    // --- Endpoints CRUD Basiques (Non sécurisés) ---

    @Override
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(
            @PageableDefault(size = DEFAULT_PAGE_SIZE_BANK_CARD, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.ok(bankCardService.getAll(pageable));
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
