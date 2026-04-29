package com.novatech.cybertech.api.controllers.implementation;


import com.novatech.cybertech.api.controllers.spec.UserManagementAdminApiSpec;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.services.core.UserManagementService;
import com.novatech.cybertech.utils.DataGenerator;
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
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_ADMIN;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(version = APP_API_VERSION, value = USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "UserAdminController", description = "API for User management (Admin)")
public class UserManagementAdminController implements UserManagementAdminApiSpec {

    // FIX(INTERFACE-CONTRACT): inject service interface instead of concrete impl per project convention
    private final UserManagementService userManagementServiceImp;

    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(
            @PageableDefault(size = DEFAULT_PAGE_SIZE_ADMIN, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(userManagementServiceImp.getAll(pageable));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserCreateRequestDto userCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementServiceImp.create(userCreateRequestDto));
    }

    @Override
    @PatchMapping(value = "/update", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> updateUser(@Valid @RequestBody final UserUpdateRequestDto userUpdateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(userManagementServiceImp.update(userUpdateRequestDto));
    }

    @Override
    @DeleteMapping(value = "/delete/{userUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteUserByUuid(@PathVariable final UUID userUuid) {
        userManagementServiceImp.deleteByUUID(userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/register/auto", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Collection<UserResponseDto>> createUserAutomatically() {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementServiceImp.createAutomatically(DataGenerator.generateUsers(100)));
    }
}