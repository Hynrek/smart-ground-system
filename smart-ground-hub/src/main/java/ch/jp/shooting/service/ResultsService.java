package ch.jp.shooting.service;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.PlayerResult;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.smartground.model.PlayerResultResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@NullMarked
public class ResultsService {
    private final LiveSessionRepository sessionRepository;

    public ResultsService(LiveSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<PlayerResultResponse> getCompetitionResults(UUID sessionId) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        return session.getPlayerResults().stream()
                .map(this::mapToPlayerResultResponse)
                .collect(Collectors.toList());
    }

    private PlayerResultResponse mapToPlayerResultResponse(PlayerResult result) {
        PlayerResultResponse response = new PlayerResultResponse();
        response.setId(result.getId());
        response.setPlayerId(result.getPlayer().getId());
        response.setSessionId(result.getSession().getId());
        response.setProgramResults(result.getProgramResults());
        
        if (result.getCreatedAt() != null) {
            response.setCreatedAt(OffsetDateTime.ofInstant(result.getCreatedAt(), ZoneOffset.UTC));
        }

        return response;
    }
}
