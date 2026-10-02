package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.BankCardAdminControllerApiSpec;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.services.core.BankCardManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_ADMIN_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_BANK_CARD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only bank-card back-office: list, read and delete any user's card. There is deliberately
 * no admin create / update — an admin never types in a customer's card number nor edits someone
 * else's card; cards are only added and edited by their owner through
 * {@link BankCardManagementController}.
 *
 * <p>OpenAPI documentation lives on {@link BankCardAdminControllerApiSpec}.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(version = APP_API_VERSION, value = BANK_CARD_ADMIN_CONTROLLER_BASE_PATH)
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
    @DeleteMapping(value = "/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteBankCardByUuid(@PathVariable UUID uuid, @AuthenticationPrincipal final Jwt jwt) {
        bankCardService.deleteByUUID(uuid, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
