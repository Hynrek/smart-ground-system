package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service für Punkteberechnung und Korrektur-Verwaltung (Delta-Modell).
 */
@Service
@NullMarked
public class ScoringService {
    private final ObjectMapper objectMapper;

    public ScoringService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Berechnet die Punkte für einen einzelnen Schritt basierend auf State und Typ.
     */
    public int calculateStepScore(StepResult step, String stepType) {
        int pointValue = getPointValue(stepType);
        return calculateStepScore(step.state, pointValue);
    }

    /**
     * Berechnet die Punkte für einen Step basierend auf State.
     * State: 'pending' | 'done' | 'failed-both' | 'failed-a' | 'failed-b'
     */
    public int calculateStepScore(String state, int pointValue) {
        return switch (state) {
            case "done" -> pointValue;
            case "failed-both" -> 0;
            case "failed-a", "failed-b" -> pointValue - 1;
            default -> 0; // pending
        };
    }

    /**
     * Gibt Punkte-Wert für einen Step-Typ zurück.
     * solo: 1, pair/a.schuss/raffale: 2
     */
    public int getPointValue(String stepType) {
        return switch (stepType.toLowerCase()) {
            case "solo" -> 1;
            case "pair", "a.schuss", "raffale" -> 2;
            default -> 1;
        };
    }

    /**
     * Berechnet die Punktabzug für einen State (für Corrections).
     */
    public int getPointDeduction(String state, int pointValue) {
        return pointValue - calculateStepScore(state, pointValue);
    }

    /**
     * Wendet eine Korrektur an und gibt den Score-Delta zurück.
     * Delta-Modell: nur die Differenz wird zur Gesamt-Punktzahl addiert/subtrahiert.
     */
    public int applyCorrection(StepResult step, String oldState, String newState, int pointValue) {
        int oldPoints = calculateStepScore(oldState, pointValue);
        int newPoints = calculateStepScore(newState, pointValue);

        // Step aktualisieren
        step.state = newState;
        step.pointsEarned = newPoints;

        // Audit-Trail hinzufügen
        Correction correction = new Correction(
                UUID.randomUUID(),
                "hit-miss",
                oldState,
                newState,
                null // correctedBy wird vom Controller gesetzt
        );
        step.corrections.add(correction);

        return newPoints - oldPoints; // Delta für Gesamt-Score
    }

    /**
     * Berechnet Segment-Score aus allen Schritt-Scores.
     */
    public int calculateSegmentScore(SegmentResult segment) {
        return segment.stepResults.stream()
                .mapToInt(s -> s.pointsEarned)
                .sum();
    }

    /**
     * Berechnet maximale Segment-Score aus Punkt-Werten aller Schritte.
     */
    public int calculateSegmentMaxScore(SegmentResult segment) {
        // Annahme: stepResults haben bereits pointValue in ihrer Struktur (oder wir nehmen
        // das generische Modell: solo=1, pair/raffale=2)
        // Für Phase 3 simplified: annahme dass maxScore bereits im Segment gespeichert ist
        return segment.maxScore;
    }

    /**
     * Berechnet Programm-Score aus allen Segment-Scores.
     */
    public int calculateProgramScore(ProgramResult program) {
        return program.segmentResults.stream()
                .mapToInt(s -> s.score)
                .sum();
    }

    /**
     * Berechnet Programm-Max-Score aus allen Segment-Max-Scores.
     */
    public int calculateProgramMaxScore(ProgramResult program) {
        return program.segmentResults.stream()
                .mapToInt(s -> s.maxScore)
                .sum();
    }

    /**
     * Berechnet Completion-Prozentsatz für einen Schritt.
     */
    public int calculateCompletionPercentage(SegmentResult segment) {
        if (segment.stepResults.isEmpty()) {
            return 0;
        }
        long completed = segment.stepResults.stream()
                .filter(s -> !s.state.equals("pending"))
                .count();
        return (int) ((completed * 100) / segment.stepResults.size());
    }
}
