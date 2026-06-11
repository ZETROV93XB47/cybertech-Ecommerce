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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = BANK_CARD_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "BankCardManagementController", description = "API for managing Bank Cards")
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

    /**
     * Frontend-gap #4 — list every bank card owned by the authenticated user.
     *
     * <p>Returns at most one card today (one-card-per-user enforced by the
     * {@code @OneToOne UserEntity.bankCardEntity}); the response is still a list to keep the
     * contract forward-compatible.</p>
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/all-mine", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BankCardResponseDto>> getAllMine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(bankCardService.findAllMine(jwt.getSubject()));
    }

}
