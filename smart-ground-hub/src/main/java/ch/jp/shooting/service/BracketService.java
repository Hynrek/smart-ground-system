package ch.jp.shooting.service;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.smartground.model.InitializeBracketRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@NullMarked
public class BracketService {
    private final LiveSessionRepository sessionRepository;

    public BracketService(LiveSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void initializeBracket(UUID sessionId, InitializeBracketRequest request) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        // Store bracket configuration
        session.setBracketType(request.getBracketType() != null ? request.getBracketType().toString() : null);
        session.setSeedingStrategy(request.getSeedingStrategy() != null ? request.getSeedingStrategy().toString() : null);

        sessionRepository.save(session);
    }
}
