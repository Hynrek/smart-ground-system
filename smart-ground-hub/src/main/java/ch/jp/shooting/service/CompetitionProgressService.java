package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import ch.jp.smartground.model.GroupProgressResponse;
import ch.jp.smartground.model.GroupProgressResponseCompletionsInner;
import ch.jp.smartground.model.SessionProgressResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    public SessionProgressResponse completeSerie(
            UUID sessionId, UUID groupId, UUID serieId,
            ch.jp.smartground.model.CompleteSerieRequest request) throws Exception {

        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        int passeIndex = request.getPasseIndex() != null && request.getPasseIndex().isPresent()
                ? request.getPasseIndex().get() : 0;
        UUID playInstanceId = request.getPlayInstanceId() != null && request.getPlayInstanceId().isPresent()
                ? request.getPlayInstanceId().get() : null;

        if (csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
                sessionId, groupId, passeIndex, serieId)) {
            throw new IllegalStateException("Serie already completed for this group");
        }

        CompetitionSerieResult csr = new CompetitionSerieResult(session, group, passeIndex, serieId);
        csr.setPlayInstanceId(playInstanceId);
        if (request.getResults() != null && !request.getResults().isEmpty()) {
            csr.setResults(objectMapper.writeValueAsString(request.getResults()));
        }
        csr = csrRepository.save(csr);

        upsertPlayerResultsFromApi(session, group, passeIndex, serieId, request.getResults());

        if (session.getStatus() == SessionStatus.ACTIVE && isAllPassenDoneForAllGroups(session)) {
            session.setStatus(SessionStatus.PRE_COMPLETE);
            sessionRepository.save(session);
        }

        return buildSessionProgressResponse(sessionId);
    }

    @Transactional(readOnly = true)
    public SessionProgressResponse getProgress(UUID sessionId) throws Exception {
        return buildSessionProgressResponse(sessionId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private boolean isAllPassenDoneForAllGroups(LiveSession session) throws Exception {
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        if (passen.length == 0) return false;

        List<ShooterGroup> groups = groupRepository.findBySessionId(session.getId());
        if (groups.isEmpty()) return false;

        // Load all CSR rows for this session in one query, then check in-memory
        List<CompetitionSerieResult> allCompleted = csrRepository.findBySessionId(session.getId());
        Set<String> completedKeys = allCompleted.stream()
            .map(c -> c.getGroup().getId() + ":" + c.getPasseIndex() + ":" + c.getSerieId())
            .collect(java.util.stream.Collectors.toSet());

        for (ShooterGroup group : groups) {
            for (int pi = 0; pi < passen.length; pi++) {
                for (String serieIdStr : passen[pi].serieIds) {
                    String key = group.getId() + ":" + pi + ":" + serieIdStr;
                    if (!completedKeys.contains(key)) return false;
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

    private SessionProgressResponse buildSessionProgressResponse(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<ShooterGroup> groups = groupRepository.findBySessionId(sessionId);
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        int passenTotal = passen.length;

        SessionProgressResponse response = new SessionProgressResponse();
        response.setSessionId(sessionId);

        for (ShooterGroup group : groups) {
            List<CompetitionSerieResult> completed =
                csrRepository.findBySessionIdAndGroupId(sessionId, group.getId());

            GroupProgressResponse gp = new GroupProgressResponse();
            gp.setGroupId(group.getId());
            gp.setGroupName(group.getName());
            gp.setPassenTotal(passenTotal);

            int passenCompleted = 0;
            List<GroupProgressResponseCompletionsInner> completions = new ArrayList<>();

            for (int pi = 0; pi < passen.length; pi++) {
                final int index = pi;
                PasseSnapshot passe = passen[pi];
                boolean allSerienDone = passe.serieIds.stream().allMatch(sid ->
                    completed.stream().anyMatch(c ->
                        c.getPasseIndex() == index && c.getSerieId().toString().equals(sid)));

                GroupProgressResponseCompletionsInner item = new GroupProgressResponseCompletionsInner();
                item.setPasseIndex(pi);
                item.setPasseName(passe.name != null ? passe.name : "Passe " + (pi + 1));
                item.setCompleted(allSerienDone);

                if (allSerienDone) {
                    passenCompleted++;
                    completed.stream()
                        .filter(c -> c.getPasseIndex() == index)
                        .map(CompetitionSerieResult::getCompletedAt)
                        .filter(t -> t != null)
                        .max(Instant::compareTo)
                        .ifPresent(t -> item.setCompletedAt(
                            org.openapitools.jackson.nullable.JsonNullable.of(
                                OffsetDateTime.ofInstant(t, ZoneOffset.UTC))));
                }
                completions.add(item);
            }

            List<ch.jp.smartground.model.GroupProgressResponseCompletedSerienInner> completedSerien =
                new ArrayList<>();
            for (CompetitionSerieResult c : completed) {
                var item = new ch.jp.smartground.model.GroupProgressResponseCompletedSerienInner();
                item.setPasseIndex(c.getPasseIndex());
                item.setSerieId(c.getSerieId());
                completedSerien.add(item);
            }

            gp.setPassenCompleted(passenCompleted);
            gp.setCompletions(completions);
            gp.setCompletedSerien(completedSerien);
            response.addGroupsItem(gp);
        }

        return response;
    }

    private void upsertPlayerResultsFromApi(LiveSession session, ShooterGroup group,
            int passeIndex, UUID serieId,
            List<ch.jp.smartground.model.PlayerResult> results) throws Exception {
        if (group.getMembers().isEmpty()) return;

        List<ch.jp.smartground.model.PlayerResult> playerScores =
            results != null ? results : List.of();

        for (SessionPlayer member : group.getMembers()) {
            int totalPoints = 0, maxPoints = 0;
            for (ch.jp.smartground.model.PlayerResult ps : playerScores) {
                if (member.getId().toString().equals(ps.getPlayerId())) {
                    totalPoints = ps.getTotalPoints() != null ? ps.getTotalPoints() : 0;
                    maxPoints   = ps.getMaxPoints()   != null ? ps.getMaxPoints()   : 0;
                    break;
                }
            }

            PlayerResult pr = playerResultRepository
                .findBySessionIdAndPlayerId(session.getId(), member.getId())
                .orElse(new PlayerResult(session, member));

            List<Map<String, Object>> existing = new ArrayList<>();
            if (pr.getProgramResults() != null) {
                @SuppressWarnings({"rawtypes", "unchecked"})
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
}
