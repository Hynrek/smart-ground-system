package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.dto.play.SerieSnapshotRecord;
import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.mapper.PlayMapper;
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
    private final SerieRepository serieRepository;
    private final PositionLabelResolver positionLabelResolver;
    private final UserScoreService userScoreService;

    public CompetitionProgressService(
            CompetitionSerieResultRepository csrRepository,
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            PlayerResultRepository playerResultRepository,
            ObjectMapper objectMapper,
            SerieRepository serieRepository,
            PositionLabelResolver positionLabelResolver,
            UserScoreService userScoreService) {
        this.csrRepository = csrRepository;
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.playerResultRepository = playerResultRepository;
        this.objectMapper = objectMapper;
        this.serieRepository = serieRepository;
        this.positionLabelResolver = positionLabelResolver;
        this.userScoreService = userScoreService;
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
        csr.setSerieSnapshotJson(buildSerieSnapshotJson(serieId));
        if (request.getResults() != null && !request.getResults().isEmpty()) {
            csr.setResults(objectMapper.writeValueAsString(request.getResults()));
        }
        csr = csrRepository.save(csr);

        writePlayerResults(session, group, passeIndex, serieId, request.getResults(), false);

        // Score-Projektion: eine Zeile pro registriertem User dieser Serie
        userScoreService.recordCompetitionSerie(csr, group, resolveSerieAlias(serieId),
            request.getResults(), false);

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

    /** Admin-Gate: gibt die nächste Passe frei, wenn die aktuelle vollständig ist. */
    public SessionProgressResponse releaseNextPasse(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Passe-Freigabe nur im Status ACTIVE möglich");
        }
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        int idx = session.getReleasedPasseIndex();
        if (idx + 1 >= passen.length) {
            throw new ConflictException("Keine weitere Passe vorhanden");
        }
        if (!isPasseDoneForAllGroups(session, idx)) {
            throw new ConflictException("Aktuelle Passe ist noch nicht von allen Rotten abgeschlossen");
        }
        session.setReleasedPasseIndex(idx + 1);
        sessionRepository.save(session);
        return buildSessionProgressResponse(sessionId);
    }

    /** Korrigiert die Ergebnisse einer bereits abgeschlossenen Serie (Admin, PRE_COMPLETE). */
    public SessionProgressResponse correctSerieResult(
            UUID sessionId, UUID groupId, UUID serieId,
            ch.jp.smartground.model.CompleteSerieRequest request) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (session.getStatus() != SessionStatus.PRE_COMPLETE) {
            throw new ConflictException("Korrektur nur im Status PRE_COMPLETE möglich");
        }
        int passeIndex = request.getPasseIndex() != null && request.getPasseIndex().isPresent()
                ? request.getPasseIndex().get() : 0;
        CompetitionSerieResult csr = csrRepository
            .findBySessionIdAndGroupIdAndPasseIndexAndSerieId(sessionId, groupId, passeIndex, serieId)
            .orElseThrow(() -> new ConflictException("Serie noch nicht abgeschlossen"));
        csr.setResults(objectMapper.writeValueAsString(request.getResults()));
        csr.setSerieSnapshotJson(buildSerieSnapshotJson(serieId));
        csrRepository.save(csr);
        writePlayerResults(session, group, passeIndex, serieId, request.getResults(), true);
        userScoreService.recordCompetitionSerie(csr, group, resolveSerieAlias(serieId),
            request.getResults(), true);
        return buildSessionProgressResponse(sessionId);
    }

    /** True when every group has completed every Serie of the given Passe index. */
    private boolean isPasseDoneForAllGroups(LiveSession session, int passeIndex) throws Exception {
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        if (passeIndex < 0 || passeIndex >= passen.length) return false;
        List<ShooterGroup> groups = groupRepository.findBySessionId(session.getId());
        if (groups.isEmpty()) return false;
        Set<String> done = csrRepository.findBySessionId(session.getId()).stream()
            .map(c -> c.getGroup().getId() + ":" + c.getPasseIndex() + ":" + c.getSerieId())
            .collect(java.util.stream.Collectors.toSet());
        for (ShooterGroup g : groups) {
            for (String serieIdStr : passen[passeIndex].serieIds) {
                if (!done.contains(g.getId() + ":" + passeIndex + ":" + serieIdStr)) return false;
            }
        }
        return true;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Anzeigename der Serie zum Abschlusszeitpunkt; Fallback auf die Id. */
    private String resolveSerieAlias(UUID serieId) {
        return serieRepository.findById(serieId)
            .map(s -> s.getName() != null ? s.getName() : serieId.toString())
            .orElse(serieId.toString());
    }

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
        response.setReleasedPasseIndex(session.getReleasedPasseIndex());

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

    private void writePlayerResults(LiveSession session, ShooterGroup group,
            int passeIndex, UUID serieId,
            List<ch.jp.smartground.model.PlayerResult> results, boolean replaceExisting) throws Exception {
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

            // Korrektur ersetzt den vorhandenen Eintrag derselben Passe/Serie; der reguläre
            // Abschluss hängt an (der Duplikat-Schutz liegt in completeSerie).
            if (replaceExisting) {
                existing.removeIf(m ->
                    serieId.toString().equals(m.get("serieId"))
                    && m.get("passeIndex") instanceof Number n && n.intValue() == passeIndex);
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

    /**
     * Baut die eingefrorene, aufgelöste Serie-Definition (Name, Platz, Step-Buchstaben)
     * zum Abschlusszeitpunkt. Gibt null zurück, wenn die Serie nicht (mehr) existiert.
     */
    @org.jspecify.annotations.Nullable
    private String buildSerieSnapshotJson(UUID serieId) throws Exception {
        Serie serie = serieRepository.findById(serieId).orElse(null);
        if (serie == null) return null;
        var resolvedSteps = positionLabelResolver.resolveSteps(
            PlayMapper.parseSteps(serie.getStepsJson()));
        var snapshot = new SerieSnapshotRecord(
            serie.getName(),
            serie.getRange() != null ? serie.getRange().getName() : null,
            resolvedSteps);
        return objectMapper.writeValueAsString(snapshot);
    }
}
