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
        for (var block : PlayMapper.parseBlocks(instance.getStateJson())) {
            if (block.result() == null) continue;
            for (var pr : block.result().playerResults()) {
                if (pr.userId() == null) continue; // anonyme Spieler und Gäste überspringen
                var row = scoreRepository.findBySourceIdAndUserId(block.blockId(), pr.userId())
                    .orElseGet(UserSerieScore::new);
                row.setUserId(pr.userId());
                row.setContext("TRAINING");
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
}
