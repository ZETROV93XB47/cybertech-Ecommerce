package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.BankCardAdminControllerApiSpec;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_ADMIN_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_BANK_CARD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only bank-card back-office. Hosts the cross-user CRUD endpoints that used to live on
 * {@link BankCardManagementController}. Injects the same {@link BankCardManagementService} — the
 * service was intentionally left unchanged in this controllers-only split (mirrors how
 * {@code OrderManagementAdminController} shares {@code OrderManagementService}).
 *
 * <p>OpenAPI documentation lives on {@link BankCardAdminControllerApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(version = APP_API_VERSION, value = BANK_CARD_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "BankCardAdminController", description = "API for Bank Card management (Admin)")
public class BankCardAdminController implements BankCardAdminControllerApiSpec {

    private final BankCardManagementService bankCardService;

    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(
            @PageableDefault(size = DEFAULT_PAGE_SIZE_BANK_CARD, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.ok(bankCardService.getAll(pageable));
    }

    @Override
    @GetMapping(value = "/get/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> getBankCardByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(bankCardService.getByUUID(uuid));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> createBankCard(@Valid @RequestBody BankCardCreationRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankCardService.create(dto));
    }

    @Override
    @PatchMapping(value = "/update", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> updateBankCardAdmin(@Valid @RequestBody BankCardUpdateRequestDto dto) {
        return ResponseEntity.ok(bankCardService.update(dto));
    }

    @Override
    @DeleteMapping(value = "/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteBankCardByUuid(@PathVariable UUID uuid) {
        bankCardService.deleteByUUID(uuid);
        return ResponseEntity.noContent().build();
    }
}
