package ch.jp.shooting.service;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.model.ShooterGroup;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.SessionPlayerRepository;
import ch.jp.shooting.repository.ShooterGroupRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.GroupCreateRequest;
import ch.jp.smartground.model.GroupResponse;
import ch.jp.smartground.model.SessionPlayerCreateRequest;
import ch.jp.smartground.model.SessionPlayerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@NullMarked
public class GroupService {
    private final ShooterGroupRepository groupRepository;
    private final LiveSessionRepository sessionRepository;
    private final SessionPlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GroupService(
            ShooterGroupRepository groupRepository,
            LiveSessionRepository sessionRepository,
            SessionPlayerRepository playerRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public GroupResponse createGroup(UUID sessionId, GroupCreateRequest request) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ShooterGroup group = new ShooterGroup();
        group.setSession(session);
        group.setName(request.getName());
        group.setCreatedAt(Instant.now());

        group = groupRepository.save(group);

        if (request.getMembers() != null) {
            for (SessionPlayerCreateRequest memberReq : request.getMembers()) {
                addPlayerToGroup(group, memberReq);
            }
        }

        group = groupRepository.save(group);
        return mapToGroupResponse(group);
    }

    public List<GroupResponse> listGroups(UUID sessionId) {
        return groupRepository.findBySessionId(sessionId).stream()
                .map(this::mapToGroupResponse)
                .collect(Collectors.toList());
    }

    private void addPlayerToGroup(ShooterGroup group, SessionPlayerCreateRequest request) {
        SessionPlayer player = new SessionPlayer();
        player.setGroup(group);
        player.setType(ch.jp.shooting.model.PlayerType.valueOf(request.getType().name()));
        player.setDisplayName(request.getDisplayName());
        player.setCreatedAt(Instant.now());

        if (request.getUserId() != null && request.getUserId().get() != null) {
            var user = userRepository.findById(request.getUserId().get())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            player.setUser(user);
        }

        playerRepository.save(player);
        group.getMembers().add(player);
    }

    private GroupResponse mapToGroupResponse(ShooterGroup group) {
        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setSessionId(group.getSession().getId());

        List<SessionPlayerResponse> members = group.getMembers().stream()
                .map(this::mapToPlayerResponse)
                .collect(Collectors.toList());
        response.setMembers(members);

        if (group.getCreatedAt() != null) {
            response.setCreatedAt(OffsetDateTime.ofInstant(group.getCreatedAt(), ZoneOffset.UTC));
        }

        return response;
    }

    private SessionPlayerResponse mapToPlayerResponse(SessionPlayer player) {
        SessionPlayerResponse response = new SessionPlayerResponse();
        response.setId(player.getId());
        response.setType(SessionPlayerResponse.TypeEnum.fromValue(player.getType().name()));
        response.setDisplayName(player.getDisplayName());
        if (player.getUser() != null) {
            response.userId(player.getUser().getId());
        }
        return response;
    }
}
