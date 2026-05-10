package ch.jp.shooting.service;

import ch.jp.shooting.dto.GroupProgress;
import ch.jp.shooting.dto.GroupResponse;
import ch.jp.shooting.dto.RangeSegmentEntry;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.ShooterGroup;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.ShooterGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Gruppen-Registrierung an Bereichen mit Validierung.
 * Verwaltet activeRangeId und activeSegmentId sowie Segment-Completion.
 */
@Service
@Transactional
@NullMarked
public class GroupRegistrationService {
    private final LiveSessionRepository sessionRepository;
    private final ShooterGroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    public GroupRegistrationService(
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Registriert eine Gruppe an einem Bereich für ein Segment.
     * Validierungen:
     * - Segment muss zum Bereich gehören (per rangeSegmentMap)
     * - Segment darf nicht bereits abgeschlossen sein (oder es ist das letzte Segment)
     * - Maximal eine Gruppe pro Bereich zur Zeit
     */
    public GroupResponse registerGroupAtRange(UUID sessionId, UUID groupId, UUID rangeId, UUID segmentId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // Validate: Segment gehört zu Range
        validateSegmentBelongsToRange(session, rangeId, segmentId);

        // Validate: Segment nicht bereits abgeschlossen
        validateSegmentNotCompleted(group, segmentId);

        // Validate: Andere Gruppe nicht schon an diesem Bereich registriert
        validateNoOtherGroupAtRange(session, groupId, rangeId);

        // Update progress: activeRangeId + activeSegmentId setzen
        updateGroupProgress(group, segmentId, rangeId, segmentId);

        group = groupRepository.save(group);
        return mapGroupToResponse(group);
    }

    /**
     * Hebt die Registrierung auf und markiert Segment als abgeschlossen.
     */
    public void unregisterGroupFromRange(UUID sessionId, UUID groupId) throws Exception {
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<GroupProgress> progresses = parseProgress(group.getProgress());

        for (GroupProgress prog : progresses) {
            if (prog.activeSegmentId != null) {
                // Segment zu completedSegmentIds hinzufügen
                if (!prog.completedSegmentIds.contains(prog.activeSegmentId)) {
                    prog.completedSegmentIds.add(prog.activeSegmentId);
                }
                // Active Werte zurücksetzen
                prog.activeRangeId = null;
                prog.activeSegmentId = null;
            }
        }

        group.setProgress(objectMapper.writeValueAsString(progresses));
        groupRepository.save(group);
    }

    /**
     * Gibt aktive und wartende Gruppen pro Bereich zurück.
     */
    public Map<String, Object> getGroupsForRange(UUID sessionId, UUID rangeId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        List<ShooterGroup> groups = session.getGroups();

        ShooterGroup activeGroup = null;
        List<ShooterGroup> queue = new ArrayList<>();

        for (ShooterGroup grp : groups) {
            List<GroupProgress> progresses = parseProgress(grp.getProgress());
            for (GroupProgress prog : progresses) {
                if (prog.activeRangeId != null && prog.activeRangeId.equals(rangeId)) {
                    activeGroup = grp;
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("activeGroup", activeGroup != null ? mapGroupToResponse(activeGroup) : null);
        result.put("queue", queue.stream().map(this::mapGroupToResponse).collect(Collectors.toList()));
        return result;
    }

    // ── Validierungsmethoden ──

    private void validateSegmentBelongsToRange(LiveSession session, UUID rangeId, UUID segmentId) throws Exception {
        if (session.getRangeSegmentMap() == null) {
            throw new IllegalArgumentException("No range-segment mapping configured");
        }

        RangeSegmentEntry[] entries = objectMapper.readValue(
                session.getRangeSegmentMap(),
                RangeSegmentEntry[].class);

        RangeSegmentEntry entry = Arrays.stream(entries)
                .filter(e -> e.rangeId.equals(rangeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Range not found in mapping"));

        if (!entry.segmentIds.contains(segmentId)) {
            throw new IllegalArgumentException("Segment not assigned to range");
        }
    }

    private void validateSegmentNotCompleted(ShooterGroup group, UUID segmentId) throws Exception {
        List<GroupProgress> progresses = parseProgress(group.getProgress());

        for (GroupProgress prog : progresses) {
            if (prog.completedSegmentIds.contains(segmentId)) {
                // Exception: Letztes Segment darf wiederholt werden
                boolean isLastSegment = prog.completedSegmentIds.size() > 0 &&
                        prog.completedSegmentIds.get(prog.completedSegmentIds.size() - 1).equals(segmentId);
                if (!isLastSegment) {
                    throw new IllegalArgumentException("Segment already completed");
                }
            }
        }
    }

    private void validateNoOtherGroupAtRange(LiveSession session, UUID groupId, UUID rangeId) throws Exception {
        for (ShooterGroup grp : session.getGroups()) {
            if (grp.getId().equals(groupId)) {
                continue; // Skip self
            }

            List<GroupProgress> progresses = parseProgress(grp.getProgress());
            for (GroupProgress prog : progresses) {
                if (prog.activeRangeId != null && prog.activeRangeId.equals(rangeId)) {
                    throw new IllegalArgumentException("Another group is already registered at this range");
                }
            }
        }
    }

    private void updateGroupProgress(ShooterGroup group, UUID segmentId, UUID activeRangeId, UUID activeSegmentId) throws Exception {
        List<GroupProgress> progresses = parseProgress(group.getProgress());

        // Progress für dieses Segment suchen oder erstellen
        GroupProgress prog = progresses.stream()
                .filter(p -> p.programId != null) // Vereinfachte Annahme: nur ein Programm pro Session
                .findFirst()
                .orElse(null);

        if (prog != null) {
            prog.activeRangeId = activeRangeId;
            prog.activeSegmentId = activeSegmentId;
        }

        group.setProgress(objectMapper.writeValueAsString(progresses));
    }

    // ── Hilfsmethoden ──

    private List<GroupProgress> parseProgress(String json) throws Exception {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(objectMapper.readValue(json, GroupProgress[].class));
    }

    private GroupResponse mapGroupToResponse(ShooterGroup group) {
        GroupResponse resp = new GroupResponse();
        resp.id = group.getId();
        resp.name = group.getName();
        resp.progress = group.getProgress();
        resp.createdAt = group.getCreatedAt();
        resp.members = new ArrayList<>(); // Spieler können geladen werden falls nötig
        return resp;
    }
}
