package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.exception.SessionNotFoundException;
import ch.jp.shooting.mapper.SessionTemplateMapper;
import ch.jp.shooting.model.SessionTemplate;
import ch.jp.shooting.model.SessionType;
import ch.jp.shooting.repository.SessionTemplateRepository;
import ch.jp.shooting.service.CompetitionService;
import ch.jp.shooting.service.SessionWebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * REST-Controller für Wettkampf-Management: Templates, Leaderboards, Karriere-Statistiken.
 */
@RestController
@RequestMapping("/api")
@NullMarked
public class CompetitionController {
    private final SessionTemplateRepository templateRepository;
    private final CompetitionService competitionService;
    private final SessionWebSocketService webSocketService;
    private final SessionTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    public CompetitionController(
            SessionTemplateRepository templateRepository,
            CompetitionService competitionService,
            SessionWebSocketService webSocketService,
            SessionTemplateMapper templateMapper,
            ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.competitionService = competitionService;
        this.webSocketService = webSocketService;
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
    }

    // ── Session-Templates CRUD ──

    /**
     * GET /api/session-templates
     * Listet alle Wettkampf-Vorlagen auf mit optionaler Filterung.
     */
    @GetMapping("/session-templates")
    public ResponseEntity<Page<CompetitionTemplateResponse>> listTemplates(
            @RequestParam(required = false) String type,
            Pageable pageable) {
        SessionType typeEnum = type != null ? SessionType.valueOf(type.toUpperCase()) : null;
        Page<SessionTemplate> templates = typeEnum != null
            ? templateRepository.findByType(typeEnum, pageable)
            : templateRepository.findAll(pageable);
        return ResponseEntity.ok(templates.map(templateMapper::toCompetitionTemplateResponse));
    }

    /**
     * POST /api/session-templates
     * Erstellt eine neue Wettkampf-Vorlage.
     */
    @PostMapping("/session-templates")
    public ResponseEntity<CompetitionTemplateResponse> createTemplate(
            @RequestBody CreateCompetitionTemplateRequest request) {
        SessionTemplate template = new SessionTemplate(
            request.name(),
            SessionType.valueOf(request.type().toUpperCase())
        );
        template.setProgramIds(request.programIds());
        template.setRangeSegmentMap(request.rangeSegmentMap());
        template.setDefaultPlayers(request.defaultPlayers());
        template.setMaxGroups(request.maxGroups());
        template.setBracketType(request.bracketType());
        template.setDefaultTiebreaker(request.defaultTiebreaker());
        template.setPublishResults(request.publishResults());

        SessionTemplate saved = templateRepository.save(template);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(templateMapper.toCompetitionTemplateResponse(saved));
    }

    /**
     * GET /api/session-templates/{id}
     * Lädt eine spezifische Wettkampf-Vorlage.
     */
    @GetMapping("/session-templates/{id}")
    public ResponseEntity<CompetitionTemplateResponse> getTemplate(@PathVariable UUID id) {
        SessionTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
        return ResponseEntity.ok(templateMapper.toCompetitionTemplateResponse(template));
    }

    /**
     * PUT /api/session-templates/{id}
     * Aktualisiert eine Wettkampf-Vorlage.
     */
    @PutMapping("/session-templates/{id}")
    public ResponseEntity<CompetitionTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @RequestBody CreateCompetitionTemplateRequest request) {
        SessionTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new SessionNotFoundException(id));

        template.setName(request.name());
        template.setType(SessionType.valueOf(request.type().toUpperCase()));
        template.setProgramIds(request.programIds());
        template.setRangeSegmentMap(request.rangeSegmentMap());
        template.setDefaultPlayers(request.defaultPlayers());
        template.setMaxGroups(request.maxGroups());
        template.setBracketType(request.bracketType());
        template.setDefaultTiebreaker(request.defaultTiebreaker());
        template.setPublishResults(request.publishResults());

        SessionTemplate saved = templateRepository.save(template);
        return ResponseEntity.ok(templateMapper.toCompetitionTemplateResponse(saved));
    }

    /**
     * DELETE /api/session-templates/{id}
     * Löscht eine Wettkampf-Vorlage.
     */
    @DeleteMapping("/session-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Leaderboards ──

    /**
     * GET /api/sessions/{id}/leaderboard
     * Gibt das aktualisierte Leaderboard für eine Sitzung zurück.
     */
    @GetMapping("/sessions/{sessionId}/leaderboard")
    public ResponseEntity<SessionLeaderboardResponse> getSessionLeaderboard(
            @PathVariable UUID sessionId) throws Exception {
        SessionLeaderboardResponse leaderboard = competitionService.computeLeaderboard(sessionId);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * GET /api/sessions/{id}/leaderboard/export
     * Exportiert das Leaderboard als JSON oder CSV.
     * Query-Parameter: format=json|csv (default: json)
     */
    @GetMapping("/sessions/{sessionId}/leaderboard/export")
    public ResponseEntity<?> exportLeaderboard(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "json") String format) throws Exception {
        SessionLeaderboardResponse leaderboard = competitionService.computeLeaderboard(sessionId);

        if ("csv".equalsIgnoreCase(format)) {
            String csv = buildLeaderboardCsv(leaderboard);
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "attachment; filename=leaderboard.csv")
                .body(csv);
        }

        // Default JSON
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=leaderboard.json")
            .body(leaderboard);
    }

    /**
     * GET /api/sessions/{id}/serie-results
     * Liefert die persistierten Serie-Ergebnisse inkl. roher Play-Daten (stepStates)
     * für die Schritt-für-Schritt-Auswertung abgeschlossener Wettkämpfe.
     */
    @GetMapping("/sessions/{sessionId}/serie-results")
    public ResponseEntity<java.util.List<CompetitionSerieResultDetailResponse>> getSerieResults(
            @PathVariable UUID sessionId) throws Exception {
        return ResponseEntity.ok(competitionService.getSerieResults(sessionId));
    }

    // ── Career Stats / Global Leaderboards ──

    /**
     * GET /api/career-stats/top-players
     * Gibt die Top-Spieler nach Gesamtpunkten zurück (globales Leaderboard).
     */
    @GetMapping("/career-stats/top-players")
    public ResponseEntity<Page<CareerStatsResponse>> getTopPlayers(Pageable pageable) {
        return ResponseEntity.ok(competitionService.getTopPlayers(pageable));
    }

    /**
     * GET /api/career-stats/top-players/wins
     * Gibt die Top-Spieler nach Siegesanzahl zurück.
     */
    @GetMapping("/career-stats/top-players/wins")
    public ResponseEntity<Page<CareerStatsResponse>> getTopPlayersByWins(Pageable pageable) {
        return ResponseEntity.ok(competitionService.getTopPlayersByWins(pageable));
    }

    /**
     * GET /api/career-stats/{userId}
     * Holt die Karriere-Statistiken für einen einzelnen Spieler.
     */
    @GetMapping("/career-stats/{userId}")
    public ResponseEntity<CareerStatsResponse> getCareerStats(@PathVariable UUID userId) {
        return ResponseEntity.ok(competitionService.getCareerStats(userId));
    }

    // ── Hilfsmetoden ──

    private String buildLeaderboardCsv(SessionLeaderboardResponse leaderboard) {
        StringBuilder csv = new StringBuilder();
        csv.append("Rank,DisplayName,TotalScore,MaxScore\n");
        for (SessionLeaderboardResponse.PlayerScoreEntry score : leaderboard.getPlayerScores()) {
            csv.append(score.getRank()).append(",")
                .append(escapeCSV(score.getDisplayName())).append(",")
                .append(score.getTotalScore()).append(",")
                .append(score.getMaxScore()).append("\n");
        }
        return csv.toString();
    }

    private String escapeCSV(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
