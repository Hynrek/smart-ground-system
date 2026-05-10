package ch.jp.shooting.service;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.exception.SessionNotFoundException;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.BracketMatchRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.ShooterGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hauptservice für Bracket-Turnier-Verwaltung.
 * Orchestriert Seeding, Generierung und Progression von Eliminierungsbrackets.
 */
@Service
@NullMarked
public class BracketService {
    private final LiveSessionRepository sessionRepository;
    private final BracketMatchRepository bracketMatchRepository;
    private final ShooterGroupRepository groupRepository;
    private final BracketSeedingService seedingService;
    private final BracketGenerationService generationService;
    private final BracketProgressionService progressionService;
    private final CompetitionService competitionService;
    private final SessionWebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    public BracketService(
            LiveSessionRepository sessionRepository,
            BracketMatchRepository bracketMatchRepository,
            ShooterGroupRepository groupRepository,
            BracketSeedingService seedingService,
            BracketGenerationService generationService,
            BracketProgressionService progressionService,
            CompetitionService competitionService,
            SessionWebSocketService webSocketService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.bracketMatchRepository = bracketMatchRepository;
        this.groupRepository = groupRepository;
        this.seedingService = seedingService;
        this.generationService = generationService;
        this.progressionService = progressionService;
        this.competitionService = competitionService;
        this.webSocketService = webSocketService;
        this.objectMapper = objectMapper;
    }

    /**
     * Initialisiert ein Bracket-Turnier mit gegebener Konfiguration.
     * Setzt den Session-Status auf SETUP und erzeugt initiale Seeding-Strukturen.
     */
    @Transactional
    public void initializeBracket(
            UUID sessionId,
            String bracketType,  // SINGLE_ELIMINATION, DOUBLE_ELIMINATION
            BracketSeedingService.SeedingStrategy seedingStrategy,
            @Nullable List<TiebreakerResolver.TiebreakerCriteria> tiebreakers) throws Exception {

        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Lade alle Spieler aus Gruppen
        List<ShooterGroup> groups = groupRepository.findBySessionId(sessionId);
        List<SessionPlayer> allPlayers = new ArrayList<>();
        for (ShooterGroup group : groups) {
            allPlayers.addAll(group.getMembers());
        }

        // Generiere Seeding basierend auf Strategie
        BracketSeedingService.SeededBracketResult seededBracket = null;
        if ("SINGLE_ELIMINATION".equals(bracketType)) {
            seededBracket = seedingService.generateSingleElimination(allPlayers, seedingStrategy, tiebreakers);
        } else if ("DOUBLE_ELIMINATION".equals(bracketType)) {
            seededBracket = seedingService.generateDoubleElimination(allPlayers, seedingStrategy, tiebreakers);
        } else {
            throw new Exception("Unknown bracket type: " + bracketType);
        }

        // Generiere Bracket-Struktur und persist Matches
        BracketGenerationService.BracketNode root = generationService.buildSingleEliminationBracket(
            seededBracket.seededPlayers.stream().map(p -> p.playerId).collect(Collectors.toList())
        );

        List<BracketMatch> matches = generationService.flattenBracketToMatches(sessionId, root);
        for (BracketMatch match : matches) {
            bracketMatchRepository.save(match);
        }

        // Speichere Bracket-State als JSON
        Map<String, Object> bracketState = new HashMap<>();
        bracketState.put("type", bracketType);
        bracketState.put("seededPlayers", seededBracket.seededPlayers);
        bracketState.put("totalByes", seededBracket.totalByes);
        bracketState.put("roundCount", seededBracket.rounds.size());

        session.setBracketPhase(BracketPhase.SETUP);
        session.setBracketStateJson(objectMapper.writeValueAsString(bracketState));
        sessionRepository.save(session);

        // Broadcast Bracket-Initialisierung
        webSocketService.publishBracketStateUpdate(sessionId, bracketState);
    }

    /**
     * Bestätigt das Seeding und startet die Bracket-Turniere.
     * Übergang von SETUP zu SEEDING Phase.
     */
    @Transactional
    public void confirmSeeding(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getBracketPhase() != BracketPhase.SETUP) {
            throw new Exception("Can only confirm seeding from SETUP phase");
        }

        BracketPhase oldPhase = session.getBracketPhase();
        session.setBracketPhase(BracketPhase.SEEDING);
        sessionRepository.save(session);

        // Broadcast Phase-Änderung
        webSocketService.publishBracketPhaseChange(sessionId, oldPhase, BracketPhase.SEEDING);
    }

    /**
     * Startet die aktive Bracket-Spielphase.
     * Übergang von SEEDING zu IN_PROGRESS Phase.
     */
    @Transactional
    public void startBracketPlay(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getBracketPhase() != BracketPhase.SEEDING) {
            throw new Exception("Can only start play from SEEDING phase");
        }

        BracketPhase oldPhase = session.getBracketPhase();
        session.setBracketPhase(BracketPhase.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        // Broadcast Phase-Änderung
        webSocketService.publishBracketPhaseChange(sessionId, oldPhase, BracketPhase.IN_PROGRESS);
    }

    /**
     * Zeichnet das Ergebnis eines Bracket-Matches auf.
     * Automatische Weiterleitung des Gewinners zur nächsten Runde.
     */
    @Transactional
    public void recordMatchWinner(UUID sessionId, int matchNumber, UUID winnerId,
                                  Integer score1, Integer score2) throws Exception {
        progressionService.recordMatchWinner(sessionId, matchNumber, winnerId, score1, score2);

        // Broadcast Match-Ergebnis
        webSocketService.publishMatchResultUpdate(sessionId, matchNumber, winnerId, score1, score2, null);

        // Prüfe, ob die nächste Runde vollständig ist
        BracketMatch match = bracketMatchRepository.findBySessionIdAndMatchNumber(sessionId, matchNumber)
            .orElseThrow(() -> new Exception("Match not found"));

        int nextRoundNum = match.getRoundNumber() + 1;
        if (progressionService.isRoundComplete(sessionId, nextRoundNum)) {
            // Broadcast Runden-Abschluss
            List<BracketMatch> nextRound = bracketMatchRepository
                .findBySessionIdAndRoundNumberOrderByMatchNumber(sessionId, nextRoundNum);
            long playedCount = progressionService.getPlayedMatchesInRound(sessionId, nextRoundNum);
            webSocketService.publishRoundCompletion(sessionId, nextRoundNum, nextRound.size(),
                (int) playedCount, null);

            // Prüfe ob Finale erreicht und abgeschlossen
            if (nextRound.size() == 1 && nextRound.get(0).getWinnerId() != null) {
                completeBracket(sessionId, nextRound.get(0).getWinnerId());
            }
        }
    }

    /**
     * Holt alle Matches für einen bestimmten Bereich.
     * Wird von Range-Operatoren verwendet.
     */
    public List<BracketMatch> getNextMatchesForRange(UUID sessionId, UUID rangeId) throws Exception {
        // TODO: Implementieren sobald Range-zu-Gruppe-Zuordnung konfiguriert
        // Für jetzt: Gebe alle nächsten Matches zurück
        BracketMatch nextMatch = progressionService.findNextUnplayedMatch(sessionId);
        if (nextMatch != null) {
            return List.of(nextMatch);
        }
        return Collections.emptyList();
    }

    /**
     * Holt den aktuellen Bracket-State.
     */
    public Map<String, Object> getBracketState(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getBracketStateJson() == null) {
            return new HashMap<>();
        }

        return objectMapper.readValue(session.getBracketStateJson(), Map.class);
    }

    /**
     * Holt die aktuelle Bracket-Phase.
     */
    @Nullable
    public BracketPhase getBracketPhase(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return session.getBracketPhase();
    }

    /**
     * Prüft, ob das Bracket-Turnier abgeschlossen ist.
     */
    public boolean isBracketComplete(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return session.getBracketPhase() == BracketPhase.COMPLETED;
    }

    /**
     * Holt das Leaderboard eines abgeschlossenen Bracket-Turniers.
     */
    public SessionLeaderboardResponse getBracketLeaderboard(UUID sessionId) throws Exception {
        return competitionService.computeLeaderboard(sessionId);
    }

    // ── Private Helfer ──

    /**
     * Markiert das Bracket als abgeschlossen und aktualisiert Karriere-Statistiken.
     */
    @Transactional
    private void completeBracket(UUID sessionId, @Nullable UUID championId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        BracketPhase oldPhase = session.getBracketPhase();
        session.setBracketPhase(BracketPhase.COMPLETED);
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);

        // Broadcast Turnier-Abschluss
        if (championId != null) {
            webSocketService.publishBracketCompleted(sessionId, championId, "Champion");
        }
        webSocketService.publishBracketPhaseChange(sessionId, oldPhase, BracketPhase.COMPLETED);

        // Aktualisiere Karriere-Statistiken
        competitionService.updateCareerStatsForSession(sessionId);
    }
}
