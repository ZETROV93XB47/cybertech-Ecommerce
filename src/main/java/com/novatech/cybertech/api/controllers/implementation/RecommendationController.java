package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.RecommendationControllerApiSpec;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.services.core.RecommendationDigestService;
import com.novatech.cybertech.services.core.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_RECOMMENDATION_COUNT;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RECOMMENDATION_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = RECOMMENDATION_CONTROLLER_BASE_PATH)
public class RecommendationController implements RecommendationControllerApiSpec {

    private final RecommendationService recommendationService;
    private final RecommendationDigestService recommendationDigestService;

    @Override
    @GetMapping(value = "/my-recommendations", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductResponseDto>> getMyRecommendations(
            @AuthenticationPrincipal final Jwt jwt,
            @RequestParam(defaultValue = "" + DEFAULT_RECOMMENDATION_COUNT) final int n
    ) {
        return ResponseEntity.ok(recommendationService.getRecommendedProducts(jwt.getSubject(), n));
    }

    /** Public (no JWT): the token of the e-mail link identifies the user. */
    @Override
    @PostMapping(value = "/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribe(@PathVariable final UUID token) {
        recommendationDigestService.unsubscribe(token);
        return ResponseEntity.noContent().build();
    }
}
