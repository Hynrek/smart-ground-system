package ch.jp.shooting.node.onboarding;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * node-api-Fläche für die Bediener-Kopplung. Hand-spezifiziert (box-api-Muster), nicht aus
 * openapi.yaml generiert. Nur die Onboarding-Scheibe — keine Provenance-/Degradations-Semantik
 * der vollen node-api-Fassade (#5). Geschützt durch den NodeApiAuthFilter (Task 9).
 */
@RestController
public class OnboardingController {

    private final PendingBoxRegistry registry;
    private final OnboardingService onboardingService;

    public OnboardingController(PendingBoxRegistry registry, OnboardingService onboardingService) {
        this.registry = registry;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/node-api/v1/onboarding/pending")
    public List<PendingBoxResponse> pending() {
        return registry.list().stream().map(PendingBoxResponse::from).toList();
    }

    @PostMapping("/node-api/v1/onboarding/{mac}/couple")
    public CoupleResult couple(@PathVariable String mac) {
        return onboardingService.couple(mac);
    }
}
