package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.CompetitionSerieResult;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.model.ShooterGroup;
import ch.jp.shooting.model.UserSerieScore;
import ch.jp.shooting.repository.UserSerieScoreRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Schreibt und liest die User-Score-Projektion (user_serie_scores).
 * Eine Zeile pro User × abgeschlossener Serie; Upsert über (sourceId, userId).
 * Nur registrierte User (userId vorhanden) erhalten Zeilen.
 */
@Service
@Transactional
@NullMarked
public class UserScoreService {

    private final UserSerieScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final SecurityHelper securityHelper;
    private final ObjectMapper objectMapper;

    public UserScoreService(UserSerieScoreRepository scoreRepository,
                            UserRepository userRepository,
                            SecurityHelper securityHelper,
                            ObjectMapper objectMapper) {
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
        this.securityHelper = securityHelper;
        this.objectMapper = objectMapper;
    }

    /** Training: beim Abschluss der ganzen Instanz eine Zeile pro Block × User schreiben. */
    public void recordTrainingInstance(PlayInstance instance) {
        // Stechen-Instanzen sind Wettkampf-Tiebreaker, keine Trainings-Serien — nie ins Score-History schreiben.
        var type = instance.getType();
        if (!"serie".equals(type) && !"passe".equals(type)) return;
        for (var block : PlayMapper.parseBlocks(instance.getStateJson())) {
            if (block.result() == null) continue;
            for (var pr : block.result().playerResults()) {
                if (pr.userId() == null) continue; // anonyme Spieler und Gäste überspringen
                var row = scoreRepository.findBySourceIdAndUserId(block.blockId(), pr.userId())
                    .orElseGet(UserSerieScore::new);
                row.setUserId(pr.userId());
                row.setContext("TRAINING");
                row.setKind("serie".equals(type) ? "SERIE" : "PASSE");
                row.setTotalPoints(pr.totalPoints());
                row.setMaxPoints(pr.maxPoints());
                row.setStepStatesJson(writeJson(pr.stepStates()));
                row.setSerieId(block.serieId());
                row.setSerieAlias(block.serieAlias());
                row.setSourceId(block.blockId());
                row.setPlayInstanceId(instance.getInstanceId());
                row.setParentName(instance.getTemplateName());
                row.setRangeId(block.rangeId());
                row.setRangeName(block.rangeName());
                row.setCompletedAt(block.completedAt() != null ? block.completedAt()
                    : instance.getCompletedAt() != null ? instance.getCompletedAt() : java.time.Instant.now());
                scoreRepository.save(row);
            }
        }
    }

    /** Wettkampf: Zeilen beim Serie-Abschluss; Korrektur ersetzt per Upsert und löscht Verwaiste. */
    public void recordCompetitionSerie(CompetitionSerieResult csr, ShooterGroup group,
                                       String serieAlias,
                                       List<ch.jp.smartground.model.PlayerResult> results,
                                       boolean replaceExisting) {
        var writtenUserIds = new java.util.HashSet<UUID>();
        for (var pr : results) {
            UUID userId = resolveUserId(group, pr);
            if (userId == null) continue;
            writtenUserIds.add(userId);
            var row = scoreRepository.findBySourceIdAndUserId(csr.getId(), userId)
                .orElseGet(UserSerieScore::new);
            row.setUserId(userId);
            row.setContext("COMPETITION");
            row.setKind("COMPETITION");
            row.setTotalPoints(pr.getTotalPoints() != null ? pr.getTotalPoints() : 0);
            row.setMaxPoints(pr.getMaxPoints() != null ? pr.getMaxPoints() : 0);
            row.setStepStatesJson(writeJson(pr.getStepStates()));
            row.setSerieId(csr.getSerieId());
            row.setSerieAlias(serieAlias);
            row.setSourceId(csr.getId());
            row.setSessionId(csr.getSession().getId());
            row.setGroupId(group.getId());
            row.setPasseIndex(csr.getPasseIndex());
            row.setParentName(csr.getSession().getName());
            row.setCompletedAt(csr.getCompletedAt());
            scoreRepository.save(row);
        }
        if (replaceExisting) {
            // Zeilen von Usern entfernen, die im korrigierten Resultat fehlen
            for (var stale : scoreRepository.findBySourceId(csr.getId())) {
                if (!writtenUserIds.contains(stale.getUserId())) {
                    scoreRepository.delete(stale);
                }
            }
        }
    }

    /** userId über das Gruppenmitglied auflösen; Fallback: userId aus dem Request. */
    @Nullable
    private UUID resolveUserId(ShooterGroup group, ch.jp.smartground.model.PlayerResult pr) {
        for (SessionPlayer member : group.getMembers()) {
            if (member.getId().toString().equals(pr.getPlayerId())) {
                return member.getUser() != null ? member.getUser().getId() : pr.getUserId();
            }
        }
        return pr.getUserId();
    }

    @Nullable
    private String writeJson(@Nullable Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null; // StepStates sind Zusatzinfo — Abschluss nicht daran scheitern lassen
        }
    }

    // ── Lesen / Aggregation ──────────────────────────────────────────────────

    private static double percent(int points, int max) {
        return max > 0 ? points * 100.0 / max : 0.0;
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.UserScorePage listMyScores(
            @Nullable String context, @Nullable String kind, @Nullable UUID serieId,
            @Nullable OffsetDateTime from, @Nullable OffsetDateTime to,
            int page, int size) {
        var userId = securityHelper.currentUser().getId();
        var fromInstant = from != null ? from.toInstant() : java.time.Instant.EPOCH;
        var toInstant = to != null ? to.toInstant() : java.time.Instant.now().plusSeconds(60);
        var result = scoreRepository.findFiltered(userId, context, kind, serieId,
            fromInstant, toInstant, org.springframework.data.domain.PageRequest.of(page, size));
        var meta = new ch.jp.smartground.model.PageMeta()
            .page(result.getNumber()).size(result.getSize())
            .totalPages(result.getTotalPages()).totalElements((int) result.getTotalElements());
        return new ch.jp.smartground.model.UserScorePage()
            .content(result.getContent().stream().map(this::toEntry).toList())
            .meta(meta);
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.UserScoreSummary getMyScoreSummary() {
        var userId = securityHelper.currentUser().getId();
        var rows = scoreRepository.findByUserIdOrderByCompletedAtDesc(userId);

        var summary = new ch.jp.smartground.model.UserScoreSummary();
        for (var context : List.of("TRAINING", "COMPETITION")) {
            var ctxRows = rows.stream().filter(r -> context.equals(r.getContext())).toList();
            var ctx = new ch.jp.smartground.model.ScoreContextSummary()
                .context(ch.jp.smartground.model.ScoreContext.fromValue(context))
                .serieCount(ctxRows.size())
                .totalPoints(ctxRows.stream().mapToInt(UserSerieScore::getTotalPoints).sum())
                .maxPoints(ctxRows.stream().mapToInt(UserSerieScore::getMaxPoints).sum())
                .averagePercent(ctxRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).average().orElse(0.0))
                .bestPercent(ctxRows.isEmpty() ? null : ctxRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).max().orElse(0.0));
            summary.addContextsItem(ctx);
        }
        return summary;
    }

    /** Trainings-Passen des aktuellen Users, gruppiert nach playInstanceId, mit Kind-Serien. */
    @Transactional(readOnly = true)
    public java.util.List<ch.jp.smartground.model.PasseScoreGroup> listMyPassen() {
        var userId = securityHelper.currentUser().getId();
        var groups = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.PasseScoreGroup>();
        for (var s : scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)) {
            if (!"PASSE".equals(s.getKind()) || s.getPlayInstanceId() == null) continue;
            var g = groups.computeIfAbsent(s.getPlayInstanceId(), k ->
                new ch.jp.smartground.model.PasseScoreGroup()
                    .key(k).label(s.getParentName())
                    .serieCount(0).totalPoints(0).maxPoints(0)
                    .lastCompletedAt(s.getCompletedAt().atOffset(java.time.ZoneOffset.UTC))
                    .serien(new java.util.ArrayList<>()));
            g.setSerieCount(g.getSerieCount() + 1);
            g.setTotalPoints(g.getTotalPoints() + s.getTotalPoints());
            g.setMaxPoints(g.getMaxPoints() + s.getMaxPoints());
            g.getSerien().add(toEntry(s));
        }
        return new java.util.ArrayList<>(groups.values());
    }

    /** Wettkämpfe des aktuellen Users: Session → Passe (passeIndex) → Serie. */
    @Transactional(readOnly = true)
    public java.util.List<ch.jp.smartground.model.WettkampfScoreGroup> listMyWettkaempfe() {
        var userId = securityHelper.currentUser().getId();
        var sessions = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.WettkampfScoreGroup>();
        var passenBySession = new java.util.HashMap<UUID, java.util.LinkedHashMap<Integer, ch.jp.smartground.model.WettkampfPasseGroup>>();
        for (var s : scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)) {
            if (!"COMPETITION".equals(s.getKind()) || s.getSessionId() == null) continue;
            var g = sessions.computeIfAbsent(s.getSessionId(), k ->
                new ch.jp.smartground.model.WettkampfScoreGroup()
                    .key(k).label(s.getParentName())
                    .serieCount(0).totalPoints(0).maxPoints(0)
                    .lastCompletedAt(s.getCompletedAt().atOffset(java.time.ZoneOffset.UTC))
                    .passen(new java.util.ArrayList<>()));
            g.setSerieCount(g.getSerieCount() + 1);
            g.setTotalPoints(g.getTotalPoints() + s.getTotalPoints());
            g.setMaxPoints(g.getMaxPoints() + s.getMaxPoints());
            int idx = s.getPasseIndex() != null ? s.getPasseIndex() : 0;
            var passeMap = passenBySession.computeIfAbsent(s.getSessionId(), k -> new java.util.LinkedHashMap<>());
            var pg = passeMap.computeIfAbsent(idx, k -> {
                var np = new ch.jp.smartground.model.WettkampfPasseGroup()
                    .passeIndex(k).totalPoints(0).maxPoints(0)
                    .serien(new java.util.ArrayList<>());
                g.getPassen().add(np);
                return np;
            });
            pg.setTotalPoints(pg.getTotalPoints() + s.getTotalPoints());
            pg.setMaxPoints(pg.getMaxPoints() + s.getMaxPoints());
            pg.getSerien().add(toEntry(s));
        }
        return new java.util.ArrayList<>(sessions.values());
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.LeaderboardResponse getLeaderboard(
            @Nullable UUID serieId, @Nullable UUID rangeId, @Nullable String context, @Nullable String kind,
            String metric, int limit, @Nullable OffsetDateTime from) {
        var fromInstant = from != null ? from.toInstant() : java.time.Instant.EPOCH;
        var rows = scoreRepository.findForLeaderboard(context, kind, serieId, rangeId, fromInstant);

        var byUser = rows.stream().collect(java.util.stream.Collectors.groupingBy(UserSerieScore::getUserId));
        var names = new java.util.HashMap<UUID, String>();
        userRepository.findAllById(byUser.keySet())
            .forEach(u -> names.put(u.getId(), u.getFullName()));

        var entries = byUser.entrySet().stream().map(e -> {
            var userRows = e.getValue();
            return new ch.jp.smartground.model.LeaderboardEntry()
                .userId(e.getKey())
                .displayName(names.getOrDefault(e.getKey(), "Unbekannt"))
                .serieCount(userRows.size())
                .bestPercent(userRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).max().orElse(0.0))
                .averagePercent(userRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).average().orElse(0.0))
                .totalPoints(userRows.stream().mapToInt(UserSerieScore::getTotalPoints).sum())
                .maxPoints(userRows.stream().mapToInt(UserSerieScore::getMaxPoints).sum());
        })
        .sorted(java.util.Comparator.comparingDouble(
            (ch.jp.smartground.model.LeaderboardEntry le) ->
                "average".equals(metric) ? le.getAveragePercent() : le.getBestPercent()).reversed())
        .limit(limit)
        .toList();

        return new ch.jp.smartground.model.LeaderboardResponse()
            .metric(ch.jp.smartground.model.LeaderboardResponse.MetricEnum.fromValue(
                "average".equals(metric) ? "average" : "best"))
            .entries(entries);
    }

    private ch.jp.smartground.model.UserSerieScoreEntry toEntry(UserSerieScore s) {
        var entry = new ch.jp.smartground.model.UserSerieScoreEntry()
            .id(s.getId())
            .context(ch.jp.smartground.model.ScoreContext.fromValue(s.getContext()))
            .kind(ch.jp.smartground.model.ScoreKind.fromValue(s.getKind()))
            .totalPoints(s.getTotalPoints())
            .maxPoints(s.getMaxPoints())
            .serieId(s.getSerieId())
            .serieAlias(s.getSerieAlias())
            .playInstanceId(s.getPlayInstanceId())
            .sessionId(s.getSessionId())
            .passeIndex(s.getPasseIndex())
            .parentName(s.getParentName())
            .rangeId(s.getRangeId())
            .rangeName(s.getRangeName())
            .completedAt(java.time.OffsetDateTime.ofInstant(s.getCompletedAt(), java.time.ZoneOffset.UTC));
        if (s.getStepStatesJson() != null) {
            try {
                entry.stepStates(java.util.Arrays.asList(objectMapper.readValue(
                    s.getStepStatesJson(), ch.jp.smartground.model.StepStateRecord[].class)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Detailkopie unlesbar — Totale bleiben gültig
            }
        }
        return entry;
    }
}
