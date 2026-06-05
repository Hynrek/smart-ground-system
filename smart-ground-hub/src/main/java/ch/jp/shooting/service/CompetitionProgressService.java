package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service für Wettkampf-Fortschrittsverfolgung.
 * Speichert Serie-Abschlüsse, aktualisiert PlayerResult pro Schütze,
 * und löst den PRE_COMPLETE-Übergang aus wenn alle Serien abgeschlossen sind.
 */
@Service
@Transactional
@NullMarked
public class CompetitionProgressService {

    private final CompetitionSerieResultRepository csrRepository;
    private final LiveSessionRepository sessionRepository;
    private final ShooterGroupRepository groupRepository;
    private final PlayerResultRepository playerResultRepository;
    private final ObjectMapper objectMapper;

    public CompetitionProgressService(
            CompetitionSerieResultRepository csrRepository,
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            PlayerResultRepository playerResultRepository,
            ObjectMapper objectMapper) {
        this.csrRepository = csrRepository;
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.playerResultRepository = playerResultRepository;
        this.objectMapper = objectMapper;
    }

    public CompetitionSerieResultResponse completeSerie(
            UUID sessionId, UUID groupId, UUID serieId,
            CompleteSerieRequest request) throws Exception {

        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
                sessionId, groupId, request.passeIndex, serieId)) {
            throw new IllegalStateException("Serie already completed for this group");
        }

        CompetitionSerieResult csr = new CompetitionSerieResult(session, group, request.passeIndex, serieId);
        csr.setPlayInstanceId(request.playInstanceId);
        if (request.results != null) {
            csr.setResults(objectMapper.writeValueAsString(request.results));
        }
        csr = csrRepository.save(csr);

        upsertPlayerResults(session, group, request.passeIndex, serieId, request.results);

        if (isAllPassenDoneForAllGroups(session)) {
            session.setStatus(SessionStatus.PRE_COMPLETE);
            sessionRepository.save(session);
        }

        return toResponse(csr);
    }

    @Transactional(readOnly = true)
    public CompetitionProgressResponse getProgress(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<ShooterGroup> groups = groupRepository.findBySessionId(sessionId);
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());

        CompetitionProgressResponse response = new CompetitionProgressResponse();
        for (ShooterGroup group : groups) {
            List<CompetitionSerieResult> completed =
                csrRepository.findBySessionIdAndGroupId(sessionId, group.getId());

            CompetitionProgressResponse.GroupProgress gp = new CompetitionProgressResponse.GroupProgress();
            gp.groupId   = group.getId();
            gp.groupName = group.getName();
            gp.currentPasseIndex = computeCurrentPasseIndex(completed, passen);

            for (CompetitionSerieResult c : completed) {
                CompetitionProgressResponse.SerieCompletion sc = new CompetitionProgressResponse.SerieCompletion();
                sc.passeIndex  = c.getPasseIndex();
                sc.serieId     = c.getSerieId();
                sc.completedAt = c.getCompletedAt();
                gp.completedSerien.add(sc);
            }
            response.groups.add(gp);
        }
        return response;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private boolean isAllPassenDoneForAllGroups(LiveSession session) throws Exception {
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        if (passen.length == 0) return false;

        List<ShooterGroup> groups = groupRepository.findBySessionId(session.getId());
        if (groups.isEmpty()) return false;

        for (ShooterGroup group : groups) {
            for (int pi = 0; pi < passen.length; pi++) {
                for (String serieIdStr : passen[pi].serieIds) {
                    if (!csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
                            session.getId(), group.getId(), pi, UUID.fromString(serieIdStr))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int computeCurrentPasseIndex(List<CompetitionSerieResult> completed, PasseSnapshot[] passen) {
        for (int pi = 0; pi < passen.length; pi++) {
            final int index = pi;
            boolean allDone = passen[pi].serieIds.stream().allMatch(sid ->
                completed.stream().anyMatch(c ->
                    c.getPasseIndex() == index && c.getSerieId().toString().equals(sid)));
            if (!allDone) return pi;
        }
        return passen.length; // all passen done
    }

    private PasseSnapshot[] parsePassen(String json) throws Exception {
        if (json == null || json.isBlank()) return new PasseSnapshot[0];
        return objectMapper.readValue(json, PasseSnapshot[].class);
    }

    @SuppressWarnings("unchecked")
    private void upsertPlayerResults(LiveSession session, ShooterGroup group,
                                     int passeIndex, UUID serieId, Object results) throws Exception {
        if (group.getMembers().isEmpty()) return;

        List<Map<String, Object>> playerScores = List.of();
        if (results != null) {
            Map<String, Object> map = objectMapper.convertValue(results, Map.class);
            Object players = map.get("players");
            if (players instanceof List<?> list) {
                playerScores = (List<Map<String, Object>>) list;
            }
        }
        final List<Map<String, Object>> scores = playerScores;

        for (SessionPlayer member : group.getMembers()) {
            int totalPoints = 0, maxPoints = 0;
            for (Map<String, Object> ps : scores) {
                if (member.getId().toString().equals(String.valueOf(ps.get("playerId")))) {
                    totalPoints = ((Number) ps.getOrDefault("totalPoints", 0)).intValue();
                    maxPoints   = ((Number) ps.getOrDefault("maxPoints",   0)).intValue();
                    break;
                }
            }

            PlayerResult pr = playerResultRepository
                .findBySessionIdAndPlayerId(session.getId(), member.getId())
                .orElse(new PlayerResult(session, member));

            List<Map<String, Object>> existing = new ArrayList<>();
            if (pr.getProgramResults() != null) {
                @SuppressWarnings("rawtypes")
                Map[] rawArray = objectMapper.readValue(pr.getProgramResults(), Map[].class);
                for (Map<?, ?> rawMap : rawArray) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedMap = (Map<String, Object>) rawMap;
                    existing.add(typedMap);
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("passeIndex",  passeIndex);
            entry.put("serieId",     serieId.toString());
            entry.put("totalPoints", totalPoints);
            entry.put("maxPoints",   maxPoints);
            entry.put("completedAt", Instant.now().toString());
            existing.add(entry);

            pr.setProgramResults(objectMapper.writeValueAsString(existing));
            pr.setUpdatedAt(Instant.now());
            playerResultRepository.save(pr);
        }
    }

    private CompetitionSerieResultResponse toResponse(CompetitionSerieResult csr) {
        CompetitionSerieResultResponse r = new CompetitionSerieResultResponse();
        r.id             = csr.getId();
        r.sessionId      = csr.getSession().getId();
        r.groupId        = csr.getGroup().getId();
        r.passeIndex     = csr.getPasseIndex();
        r.serieId        = csr.getSerieId();
        r.playInstanceId = csr.getPlayInstanceId();
        r.completedAt    = csr.getCompletedAt();
        return r;
    }
}
