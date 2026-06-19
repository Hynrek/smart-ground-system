package ch.jp.shooting.service;

import ch.jp.shooting.exception.InvalidTiebreakerStateException;
import ch.jp.shooting.exception.TiebreakerNotFoundException;
import ch.jp.shooting.model.CompetitionTiebreaker;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.PlayerResult;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.PlayerType;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.model.SessionStatus;
import ch.jp.shooting.model.TiebreakerStatus;
import ch.jp.shooting.repository.CompetitionTiebreakerRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.shooting.repository.SessionPlayerRepository;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.SessionTiesResponse;
import ch.jp.smartground.model.StartTiebreakerRequest;
import ch.jp.smartground.model.SubmitTiebreakerResultsRequest;
import ch.jp.smartground.model.TiebreakerParticipant;
import ch.jp.smartground.model.TiebreakerPlayerScore;
import ch.jp.smartground.model.TiebreakerResponse;
import ch.jp.smartground.model.TiedBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Integrations-Kern des Wettkampf-Stechens (Tiebreaker): listet Gleichstände auf,
 * startet eine Stechen-Runde als Einzel-Serie-Lauf und nimmt deren Ergebnisse entgegen.
 *
 * <p>Wichtig: Stechen-Ergebnisse landen NIE in {@link PlayerResult} — sie ordnen nur
 * den gleichstehenden Block. Die Hauptpunkte bleiben unangetastet.
 */
@Service
@Transactional
@NullMarked
public class TiebreakerService {

    private final LiveSessionRepository sessionRepo;
    private final CompetitionTiebreakerRepository tbRepo;
    private final PlayerResultRepository playerResultRepo;
    private final SessionPlayerRepository playerRepo;
    private final PlayInstanceService playInstanceService;
    private final SerieRepository serieRepo;
    private final ObjectMapper objectMapper;
    private final TieResolver tieResolver;

    public TiebreakerService(LiveSessionRepository sessionRepo,
                             CompetitionTiebreakerRepository tbRepo,
                             PlayerResultRepository playerResultRepo,
                             SessionPlayerRepository playerRepo,
                             PlayInstanceService playInstanceService,
                             SerieRepository serieRepo,
                             ObjectMapper objectMapper,
                             TieResolver tieResolver) {
        this.sessionRepo = sessionRepo;
        this.tbRepo = tbRepo;
        this.playerResultRepo = playerResultRepo;
        this.playerRepo = playerRepo;
        this.playInstanceService = playInstanceService;
        this.serieRepo = serieRepo;
        this.objectMapper = objectMapper;
        this.tieResolver = tieResolver;
    }

    // ── Gleichstände auflisten ─────────────────────────────────────────────────

    /** Ermittelt alle punktgleichen Blöcke der Session inkl. ihrer Stechen-Runden. */
    public SessionTiesResponse listTies(UUID sessionId) throws Exception {
        // 1) Hauptstände je Spieler aus den PlayerResults aufbauen.
        var results = playerResultRepo.findBySessionId(sessionId);
        List<TieResolver.PlayerStanding> standings = new ArrayList<>();
        for (PlayerResult pr : results) {
            int[] tm = sumPoints(pr.getProgramResults());
            standings.add(new TieResolver.PlayerStanding(
                    pr.getPlayer().getId(), pr.getPlayer().getDisplayName(), tm[0], tm[1]));
        }

        // 2) Abgeschlossene Stechen-Runden in TieResolver-Runden übersetzen.
        var tiebreakers = tbRepo.findBySessionId(sessionId);
        List<TieResolver.TiebreakerRound> rounds = new ArrayList<>();
        for (CompetitionTiebreaker tb : tiebreakers) {
            if (tb.getStatus() != TiebreakerStatus.COMPLETED || tb.getResultsJson() == null) {
                continue;
            }
            List<UUID> participantIds = parseUuidList(tb.getParticipantsJson());
            Map<UUID, Integer> scores = parseScores(tb.getResultsJson());
            rounds.add(new TieResolver.TiebreakerRound(
                    tb.getTieGroupId(), tb.getRoundNumber(), participantIds, scores));
        }

        // 3) Auflösen.
        var resolved = tieResolver.resolve(standings, rounds);

        // 4) Aufeinanderfolgende Einträge gleicher Hauptpunkte (Blockgröße > 1) zu TiedBlocks gruppieren.
        SessionTiesResponse response = new SessionTiesResponse().sessionId(sessionId);
        int i = 0;
        while (i < resolved.size()) {
            int j = i;
            while (j + 1 < resolved.size()
                    && resolved.get(j + 1).totalScore() == resolved.get(i).totalScore()) {
                j++;
            }
            int blockSize = j - i + 1;
            if (blockSize > 1) {
                var block = resolved.subList(i, j + 1);
                response.addTiedBlocksItem(toTiedBlock(sessionId, block, tiebreakers));
            }
            i = j + 1;
        }
        return response;
    }

    /** Baut einen TiedBlock aus einem punktgleichen Abschnitt der aufgelösten Stände. */
    private TiedBlock toTiedBlock(UUID sessionId,
                                  List<TieResolver.ResolvedStanding> block,
                                  List<CompetitionTiebreaker> allTiebreakers) throws Exception {
        // Höchster Rang im Block = Stechen-Position.
        int tiePosition = block.stream().mapToInt(TieResolver.ResolvedStanding::rank).min().orElse(1);
        int sharedScore = block.get(0).totalScore();
        boolean resolved = block.stream().noneMatch(TieResolver.ResolvedStanding::tied);

        Set<UUID> blockIds = new HashSet<>();
        var players = new ArrayList<TiebreakerParticipant>();
        for (var rs : block) {
            blockIds.add(rs.playerId());
            players.add(new TiebreakerParticipant()
                    .playerId(rs.playerId())
                    .displayName(rs.displayName()));
        }

        // Alle Stechen dieser Session, deren Teilnehmer den Block schneiden — nach Runde geordnet.
        var rounds = new ArrayList<TiebreakerResponse>();
        var matching = new ArrayList<CompetitionTiebreaker>();
        for (CompetitionTiebreaker tb : allTiebreakers) {
            List<UUID> participants = parseUuidList(tb.getParticipantsJson());
            if (participants.stream().anyMatch(blockIds::contains)) {
                matching.add(tb);
            }
        }
        matching.sort(Comparator.comparingInt(CompetitionTiebreaker::getRoundNumber));
        for (CompetitionTiebreaker tb : matching) {
            rounds.add(toResponse(tb));
        }

        return new TiedBlock()
                .tiePosition(tiePosition)
                .sharedScore(sharedScore)
                .resolved(resolved)
                .players(players)
                .rounds(rounds);
    }

    // ── Stechen-Runde starten ──────────────────────────────────────────────────

    /** Startet eine neue Stechen-Runde als Live-Lauf einer Einzel-Serie für die gleichstehenden Spieler. */
    public TiebreakerResponse startTiebreaker(UUID sessionId, StartTiebreakerRequest req) throws Exception {
        LiveSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new TiebreakerNotFoundException(sessionId));
        if (session.getStatus() != SessionStatus.PRE_COMPLETE) {
            throw new InvalidTiebreakerStateException("Stechen nur im Status PRE_COMPLETE möglich");
        }

        int tiePosition = req.getTiePosition() != null ? req.getTiePosition() : 1;
        List<UUID> playerIds = req.getPlayerIds() != null ? req.getPlayerIds() : List.of();
        Set<UUID> requested = new HashSet<>(playerIds);

        // tieGroupId / roundNumber: bestehende Runde derselben Position wiederverwenden,
        // wenn ihre Teilnehmer eine Obermenge der angefragten Spieler sind.
        UUID tieGroupId = null;
        int roundNumber = 1;
        var existing = tbRepo.findBySessionId(sessionId);
        for (CompetitionTiebreaker tb : existing) {
            if (tb.getTiePosition() != tiePosition) {
                continue;
            }
            Set<UUID> participants = new HashSet<>(parseUuidList(tb.getParticipantsJson()));
            if (participants.containsAll(requested)) {
                if (tieGroupId == null) {
                    tieGroupId = tb.getTieGroupId();
                }
                if (tb.getTieGroupId().equals(tieGroupId)) {
                    roundNumber = Math.max(roundNumber, tb.getRoundNumber() + 1);
                }
            }
        }
        if (tieGroupId == null) {
            tieGroupId = UUID.randomUUID();
            roundNumber = 1;
        }

        // Serie-Vorlage laden — ein Stechen ist immer eine Einzel-Serie.
        Serie serie = serieRepo.findById(req.getTemplateId())
                .orElseThrow(() -> new TiebreakerNotFoundException(req.getTemplateId()));
        String snapshot = buildSerieSnapshot(serie);

        // Tiebreaker anlegen.
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, tieGroupId, roundNumber, tiePosition);
        tb.setTemplateId(serie.getId());
        tb.setTemplateName(serie.getName());
        tb.setProgramSnapshot(snapshot); // unveränderlicher Ablauf-Snapshot (genau eine Serie)
        tb.setParticipantsJson(objectMapper.writeValueAsString(
                playerIds.stream().map(UUID::toString).toList()));

        // Live-Lauf: Einzel-Serie-Lauf für die gleichstehenden Spieler starten.
        var tiedPlayers = playerRepo.findAllById(playerIds);
        var playerRefs = tiedPlayers.stream()
                .map(p -> new PlayerRef()
                        .id(p.getId().toString())
                        .type(mapPlayerType(p.getType()))
                        .displayName(p.getDisplayName()))
                .toList();
        var instance = playInstanceService.startSerieInstance(
                serie.getId(), serie.getName(), snapshot, playerRefs);
        tb.setPlayInstanceId(instance.getInstanceId());

        tb.setStatus(TiebreakerStatus.ACTIVE);
        return toResponse(tbRepo.save(tb));
    }

    /**
     * Baut aus einer einzelnen Serie einen Serien-Listen-Snapshot in der Form, die
     * {@code PlayMapper.parseEmbeddedSerien} konsumiert: {@code [{id, alias, rangeId, rangeName, steps}]}.
     */
    private String buildSerieSnapshot(Serie serie) throws Exception {
        ObjectNode serieNode = objectMapper.createObjectNode();
        serieNode.put("id", serie.getId().toString());
        serieNode.put("alias", serie.getName());
        if (serie.getRange() != null) {
            serieNode.put("rangeId", serie.getRange().getId().toString());
            serieNode.put("rangeName", serie.getRange().getName());
        }
        String steps = serie.getStepsJson();
        serieNode.set("steps", objectMapper.readTree(steps == null || steps.isBlank() ? "[]" : steps));
        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add(serieNode);
        return objectMapper.writeValueAsString(arrayNode);
    }

    // ── Stechen-Ergebnisse entgegennehmen ──────────────────────────────────────

    /**
     * Speichert die Ergebnisse einer Stechen-Runde und schliesst sie ab.
     * Verändert bewusst NIE einen PlayerResult — die Hauptpunkte bleiben unangetastet.
     */
    public SessionTiesResponse submitResults(UUID sessionId, UUID tiebreakerId,
                                             SubmitTiebreakerResultsRequest req) throws Exception {
        CompetitionTiebreaker tb = tbRepo.findById(tiebreakerId)
                .orElseThrow(() -> new TiebreakerNotFoundException(tiebreakerId));
        if (tb.getStatus() == TiebreakerStatus.COMPLETED) {
            throw new InvalidTiebreakerStateException("Stechen-Runde bereits abgeschlossen");
        }

        var results = req.getResults() != null ? req.getResults() : List.<TiebreakerPlayerScore>of();
        tb.setResultsJson(objectMapper.writeValueAsString(results));
        tb.setStatus(TiebreakerStatus.COMPLETED);
        tb.setCompletedAt(Instant.now());
        tbRepo.save(tb);

        return listTies(sessionId);
    }

    // ── Stechen auflisten ──────────────────────────────────────────────────────

    /** Listet alle Stechen-Runden einer Session (nach Runde geordnet). */
    public List<TiebreakerResponse> listTiebreakers(UUID sessionId) throws Exception {
        var tiebreakers = new ArrayList<>(tbRepo.findBySessionId(sessionId));
        tiebreakers.sort(Comparator.comparingInt(CompetitionTiebreaker::getRoundNumber));
        var out = new ArrayList<TiebreakerResponse>();
        for (CompetitionTiebreaker tb : tiebreakers) {
            out.add(toResponse(tb));
        }
        return out;
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    /** Bildet einen CompetitionTiebreaker auf die OpenAPI-Antwort ab. */
    private TiebreakerResponse toResponse(CompetitionTiebreaker tb) throws Exception {
        var response = new TiebreakerResponse()
                .id(tb.getId())
                .sessionId(tb.getSession().getId())
                .tieGroupId(tb.getTieGroupId())
                .roundNumber(tb.getRoundNumber())
                .tiePosition(tb.getTiePosition())
                .status(TiebreakerResponse.StatusEnum.fromValue(tb.getStatus().name()))
                .templateId(tb.getTemplateId())
                .templateName(tb.getTemplateName());
        if (tb.getPlayInstanceId() != null) {
            response.playInstanceId(tb.getPlayInstanceId());
        }

        // Teilnehmer auflösen (playerId → displayName über die SessionPlayer).
        List<UUID> participantIds = parseUuidList(tb.getParticipantsJson());
        Map<UUID, String> names = new LinkedHashMap<>();
        for (SessionPlayer p : playerRepo.findAllById(participantIds)) {
            names.put(p.getId(), p.getDisplayName());
        }
        var participants = new ArrayList<TiebreakerParticipant>();
        for (UUID pid : participantIds) {
            participants.add(new TiebreakerParticipant()
                    .playerId(pid)
                    .displayName(names.get(pid)));
        }
        response.participants(participants);

        // Ergebnisse aus resultsJson parsen.
        if (tb.getResultsJson() != null && !tb.getResultsJson().isBlank()) {
            var node = objectMapper.readTree(tb.getResultsJson());
            var scores = new ArrayList<TiebreakerPlayerScore>();
            if (node.isArray()) {
                for (JsonNode s : node) {
                    var score = new TiebreakerPlayerScore()
                            .totalPoints(s.path("totalPoints").asInt(0))
                            .maxPoints(s.path("maxPoints").asInt(0));
                    String pid = s.path("playerId").asText(null);
                    if (pid != null && !pid.isBlank()) {
                        score.playerId(UUID.fromString(pid));
                    }
                    scores.add(score);
                }
            }
            response.results(scores);
        }
        return response;
    }

    private PlayerRef.TypeEnum mapPlayerType(PlayerType type) {
        return type == PlayerType.GUEST ? PlayerRef.TypeEnum.GUEST : PlayerRef.TypeEnum.USER;
    }

    // ── Punktesummierung (identisch zu CompetitionService) ──────────────────────

    /** Summiert totalPoints/maxPoints über die flache Serien-Liste in programResults. */
    private int[] sumPoints(String raw) throws Exception {
        int playerTotal = 0;
        int playerMax = 0;
        var node = objectMapper.readTree(raw == null || raw.isBlank() ? "[]" : raw);
        if (node.isArray()) {
            for (var serie : node) {
                playerTotal += serie.path("totalPoints").asInt(0);
                playerMax += serie.path("maxPoints").asInt(0);
            }
        }
        return new int[]{playerTotal, playerMax};
    }

    /** Parst eine JSON-Array-Liste von UUID-Strings. */
    private List<UUID> parseUuidList(String json) throws Exception {
        var node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        var ids = new ArrayList<UUID>();
        if (node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText(null);
                if (s != null && !s.isBlank()) {
                    ids.add(UUID.fromString(s));
                }
            }
        }
        return ids;
    }

    /** Parst resultsJson [{playerId,totalPoints,...}] zu playerId → totalPoints. */
    private Map<UUID, Integer> parseScores(String json) throws Exception {
        var node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        var scores = new LinkedHashMap<UUID, Integer>();
        if (node.isArray()) {
            for (JsonNode s : node) {
                String pid = s.path("playerId").asText(null);
                if (pid != null && !pid.isBlank()) {
                    scores.put(UUID.fromString(pid), s.path("totalPoints").asInt(0));
                }
            }
        }
        return scores;
    }
}
