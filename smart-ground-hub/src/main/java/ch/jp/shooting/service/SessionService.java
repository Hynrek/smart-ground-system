package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Session-Verwaltung: CRUD, Status-Übergänge, Gruppen-Management.
 */
@Service
@Transactional
@NullMarked
public class SessionService {
    private final LiveSessionRepository sessionRepository;
    private final ShooterGroupRepository groupRepository;
    private final SessionPlayerRepository playerRepository;
    private final PlayerResultRepository resultRepository;
    private final SessionTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SessionService(
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            SessionPlayerRepository playerRepository,
            PlayerResultRepository resultRepository,
            SessionTemplateRepository templateRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.resultRepository = resultRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Erstellt eine neue Session mit Gruppen und Spielern.
     */
    public SessionResponse createSession(CreateSessionRequest req) throws Exception {
        LiveSession session = new LiveSession();
        session.setType(SessionType.valueOf(req.type.toUpperCase()));
        session.setStatus(SessionStatus.SETUP);

        if (req.name != null && !req.name.isBlank()) {
            session.setName(req.name);
        }

        // Template laden falls vorhanden
        if (req.templateId != null) {
            SessionTemplate template = templateRepository.findById(req.templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
            session.setTemplate(template);
            // Snapshots vom Template laden falls nicht überschrieben
            if (req.programs == null) {
                session.setProgramSnapshots(template.getProgramIds());
            }
            if (req.rangeSegmentMap == null && template.getRangeSegmentMap() != null) {
                session.setRangeSegmentMap(template.getRangeSegmentMap());
            }
        }

        // Programme als JSON speichern
        if (req.programs != null) {
            session.setProgramSnapshots(objectMapper.writeValueAsString(req.programs));
        }

        if (req.passen != null) {
            try {
                session.setProgramSnapshots(objectMapper.writeValueAsString(req.passen));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize passen", e);
            }
        }

        // Bereichs-Segment-Zuordnung speichern
        if (req.rangeSegmentMap != null) {
            session.setRangeSegmentMap(objectMapper.writeValueAsString(req.rangeSegmentMap));
        }

        session = sessionRepository.save(session);

        // Gruppen und Spieler erstellen
        if (req.groups != null) {
            for (GroupCreateRequest grp : req.groups) {
                createGroup(session, grp);
            }
        }

        return mapSessionToResponse(session);
    }

    /**
     * Listet Sessions auf (mit Pagination).
     */
    public Page<SessionResponse> listSessions(
            @Nullable SessionStatus status,
            @Nullable SessionType type,
            Pageable pageable) {
        Page<LiveSession> page;
        if (status != null && type != null) {
            page = sessionRepository.findByTypeAndStatus(type, status, pageable);
        } else if (status != null) {
            page = sessionRepository.findByStatus(status, pageable);
        } else if (type != null) {
            page = sessionRepository.findByType(type, pageable);
        } else {
            page = sessionRepository.findAll(pageable);
        }
        return page.map(this::mapSessionToResponse);
    }

    /**
     * Aktualisiert den Session-Status.
     */
    public SessionResponse updateSessionStatus(UUID sessionId, String newStatus) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        SessionStatus status = SessionStatus.valueOf(newStatus.toUpperCase());
        validateStatusTransition(session.getStatus(), status);

        session.setStatus(status);
        if (status == SessionStatus.ACTIVE) {
            session.setStartedAt(Instant.now());
        } else if (status == SessionStatus.COMPLETED) {
            session.setCompletedAt(Instant.now());
        }

        session = sessionRepository.save(session);
        return mapSessionToResponse(session);
    }

    /**
     * Erstellt eine Gruppe in einer Session (nur im SETUP-Status).
     */
    public GroupResponse createGroup(LiveSession session, GroupCreateRequest req) {
        if (session.getStatus() != SessionStatus.SETUP) {
            throw new IllegalStateException("Cannot add groups after session started");
        }

        ShooterGroup group = new ShooterGroup(session, req.name);
        group = groupRepository.save(group);

        // Spieler hinzufügen
        if (req.members != null) {
            for (SessionPlayerCreateRequest playerReq : req.members) {
                addPlayerToGroup(group, playerReq);
            }
        }

        // Progress initialisieren (leere Liste)
        List<GroupProgress> progress = new ArrayList<>();
        try {
            group.setProgress(objectMapper.writeValueAsString(progress));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize group progress", e);
        }
        group = groupRepository.save(group);

        return mapGroupToResponse(group);
    }

    /**
     * Fügt einen Spieler zu einer Gruppe hinzu.
     */
    private void addPlayerToGroup(ShooterGroup group, SessionPlayerCreateRequest req) {
        SessionPlayer player = new SessionPlayer();
        player.setGroup(group);
        player.setType(PlayerType.valueOf(req.type.toUpperCase()));
        player.setDisplayName(req.displayName);
        player.setPaid(req.paid);

        if (req.userId != null && req.type.equalsIgnoreCase("USER")) {
            User user = userRepository.findById(req.userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            player.setUser(user);
        }

        playerRepository.save(player);
        group.getMembers().add(player);
    }

    /**
     * Aktualisiert eine Gruppe (nur im SETUP-Status).
     */
    public GroupResponse updateGroup(UUID sessionId, UUID groupId, GroupCreateRequest req) {
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (group.getSession().getStatus() != SessionStatus.SETUP) {
            throw new IllegalStateException("Cannot update group after session started");
        }

        group.setName(req.name);
        group = groupRepository.save(group);
        return mapGroupToResponse(group);
    }

    /**
     * Löscht eine Gruppe (nur im SETUP-Status).
     */
    public void deleteGroup(UUID sessionId, UUID groupId) {
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (group.getSession().getStatus() != SessionStatus.SETUP) {
            throw new IllegalStateException("Cannot delete group after session started");
        }

        groupRepository.delete(group);
    }

    /**
     * Gibt alle Gruppen einer Session zurück.
     */
    public List<GroupResponse> getGroups(UUID sessionId) {
        return groupRepository.findBySessionId(sessionId).stream()
                .map(this::mapGroupToResponse)
                .collect(Collectors.toList());
    }

    // ── Hilfsmethoden ──

    private void validateStatusTransition(SessionStatus from, SessionStatus to) {
        if (to == SessionStatus.ABANDONED) return; // any status can be abandoned
        if (from == SessionStatus.COMPLETED || from == SessionStatus.ABANDONED) {
            throw new IllegalStateException("Cannot transition from terminal status " + from);
        }
        boolean valid = switch (from) {
            case SETUP -> to == SessionStatus.OPEN;
            case OPEN  -> to == SessionStatus.ACTIVE;
            case ACTIVE -> to == SessionStatus.PRE_COMPLETE;
            case PRE_COMPLETE -> to == SessionStatus.COMPLETED;
            default -> false;
        };
        if (!valid) throw new IllegalStateException("Invalid transition: " + from + " → " + to);
    }

    private SessionResponse mapSessionToResponse(LiveSession session) {
        SessionResponse resp = new SessionResponse();
        resp.id = session.getId();
        resp.name = session.getName();
        resp.type = session.getType().name().toLowerCase();
        resp.status = session.getStatus().name().toLowerCase();
        resp.templateId = session.getTemplate() != null ? session.getTemplate().getId() : null;
        resp.programSnapshots = session.getProgramSnapshots();
        resp.rangeSegmentMap = session.getRangeSegmentMap();
        resp.startedAt = session.getStartedAt();
        resp.completedAt = session.getCompletedAt();
        resp.createdAt = session.getCreatedAt();

        // Gruppen laden (LAZY)
        resp.groups = session.getGroups().stream()
                .map(this::mapGroupToResponse)
                .collect(Collectors.toList());

        // Ergebnisse laden (LAZY)
        resp.playerResults = session.getPlayerResults().stream()
                .map(this::mapPlayerResultToResponse)
                .collect(Collectors.toList());

        return resp;
    }

    private GroupResponse mapGroupToResponse(ShooterGroup group) {
        GroupResponse resp = new GroupResponse();
        resp.id = group.getId();
        resp.name = group.getName();
        resp.progress = group.getProgress();
        resp.createdAt = group.getCreatedAt();

        resp.members = group.getMembers().stream()
                .map(this::mapPlayerToResponse)
                .collect(Collectors.toList());

        return resp;
    }

    private SessionPlayerResponse mapPlayerToResponse(SessionPlayer player) {
        SessionPlayerResponse resp = new SessionPlayerResponse();
        resp.id = player.getId();
        resp.type = player.getType().name();
        resp.displayName = player.getDisplayName();
        resp.userId = player.getUser() != null ? player.getUser().getId() : null;
        resp.createdAt = player.getCreatedAt();
        resp.paid = player.isPaid();
        return resp;
    }

    private PlayerResultResponse mapPlayerResultToResponse(PlayerResult result) {
        PlayerResultResponse resp = new PlayerResultResponse();
        resp.id = result.getId();
        resp.playerId = result.getPlayer().getId();
        resp.displayName = result.getPlayer().getDisplayName();
        resp.programResults = result.getProgramResults();
        resp.createdAt = result.getCreatedAt();
        resp.updatedAt = result.getUpdatedAt();

        // Scores berechnen (aus JSON)
        computeResultScores(result, resp);

        return resp;
    }

    private void computeResultScores(PlayerResult entity, PlayerResultResponse resp) {
        int totalScore = 0;
        int maxScore = 0;
        int completedSteps = 0;
        int totalSteps = 0;

        if (entity.getProgramResults() != null) {
            try {
                ProgramResult[] programs = objectMapper.readValue(
                        entity.getProgramResults(),
                        ProgramResult[].class);

                for (ProgramResult prog : programs) {
                    for (SegmentResult seg : prog.segmentResults) {
                        totalScore += seg.score;
                        maxScore += seg.maxScore;

                        for (StepResult step : seg.stepResults) {
                            totalSteps++;
                            if (!step.state.equals("pending")) {
                                completedSteps++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // JSON parsing fehler — skip
            }
        }

        resp.totalScore = totalScore;
        resp.maxScore = maxScore;
        resp.completionPct = totalSteps > 0 ? (completedSteps * 100) / totalSteps : 0;
    }

    // ── API Adapter Methods (ch.jp.smartground.model) ──

    public ch.jp.smartground.model.SessionResponse createSession(ch.jp.smartground.model.CreateSessionRequest req) {
        LiveSession session = new LiveSession();
        if (req.getName() != null) {
            session.setName(req.getName());
        }
        if (req.getDescription() != null && req.getDescription().get() != null) {
            session.setDescription(req.getDescription().get());
        }
        session.setType(SessionType.valueOf(req.getSessionType().name()));
        session.setStatus(SessionStatus.SETUP);
        session.setCreatedAt(Instant.now());

        session = sessionRepository.save(session);
        return mapToApiSessionResponse(session);
    }

    public ch.jp.smartground.model.SessionResponse getSession(UUID sessionId) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        return mapToApiSessionResponse(session);
    }

    public ch.jp.smartground.model.SessionPageResponse listSessions(Integer page, Integer size) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        Page<LiveSession> sessions = sessionRepository.findAll(pageable);

        ch.jp.smartground.model.SessionPageResponse response = new ch.jp.smartground.model.SessionPageResponse();
        response.setContent(sessions.getContent().stream()
                .map(this::mapToApiSessionResponse)
                .collect(Collectors.toList()));

        ch.jp.smartground.model.PageMeta meta = new ch.jp.smartground.model.PageMeta();
        meta.setPage(sessions.getNumber());
        meta.setSize(sessions.getSize());
        meta.setTotalElements((int) sessions.getTotalElements());
        meta.setTotalPages(sessions.getTotalPages());
        response.setMeta(meta);

        return response;
    }

    private ch.jp.smartground.model.SessionResponse mapToApiSessionResponse(LiveSession session) {
        ch.jp.smartground.model.SessionResponse response = new ch.jp.smartground.model.SessionResponse();
        response.setId(session.getId());
        if (session.getName() != null) {
            response.name(session.getName());
        }
        if (session.getDescription() != null) {
            response.description(session.getDescription());
        }
        response.setStatus(ch.jp.smartground.model.SessionResponse.StatusEnum.fromValue(session.getStatus().name()));
        response.setSessionType(ch.jp.smartground.model.SessionResponse.SessionTypeEnum.fromValue(session.getType().name()));

        if (session.getCreatedAt() != null) {
            response.createdAt(java.time.OffsetDateTime.ofInstant(session.getCreatedAt(), java.time.ZoneOffset.UTC));
        }
        if (session.getStartedAt() != null) {
            response.startedAt(java.time.OffsetDateTime.ofInstant(session.getStartedAt(), java.time.ZoneOffset.UTC));
        }
        if (session.getCompletedAt() != null) {
            response.completedAt(java.time.OffsetDateTime.ofInstant(session.getCompletedAt(), java.time.ZoneOffset.UTC));
        }

        return response;
    }
}
