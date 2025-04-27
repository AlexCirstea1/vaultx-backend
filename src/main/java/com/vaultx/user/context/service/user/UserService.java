package com.vaultx.user.context.service.user;

import com.vaultx.user.context.exception.UserNotFoundException;
import com.vaultx.user.context.model.PublicKeyResponse;
import com.vaultx.user.context.model.activity.ActivityResponseDTO;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.authentication.response.UserSearchDTO;
import com.vaultx.user.context.model.blockchain.EventType;
import com.vaultx.user.context.model.user.RoleType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.model.user.UserReportRequest;
import com.vaultx.user.context.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final ActivityService activityService;
    private final BlockchainService blockchainService;
    private final BlockService blockService;
    private final ReportService reportService;
    private final SessionService sessionService;
    private final KeyManagementService keyManagementService;

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository
                .findUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public User getUserById(UUID id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }

    public User getSessionUser(HttpServletRequest request) {
        return sessionService.getSessionUser(request);
    }

    public UserResponseDTO getUser(HttpServletRequest request) {
        return sessionService.getUser(request);
    }

    public UserResponseDTO getUserData(String id) {
        User user = getUserById(UUID.fromString(id));
        UserResponseDTO userDto = modelMapper.map(user, UserResponseDTO.class);
        userDto.setHasPin(user.getPin() != null);
        return userDto;
    }

    public String deleteUser(HttpServletRequest request) {
        return sessionService.deleteUser(request);
    }

    @Transactional(readOnly = true)
    public String getUserAvatar(UUID id) {
        User user = getUserById(id);
        if (user.getProfileImage() == null) {
            throw new UserNotFoundException("Avatar not found for user: " + id);
        }
        return user.getProfileImage();
    }

    public PublicKeyResponse getUserPublicKey(UUID id) {
        return keyManagementService.getUserPublicKey(id).getBody();
    }

    public String savePublicKey(HttpServletRequest request, String publicKey) {
        return keyManagementService.savePublicKey(request, publicKey).getBody();
    }

    public String reportUser(HttpServletRequest request, UserReportRequest reportRequest) {
        if (reportRequest.getUserId() == null || reportRequest.getReason() == null) {
            throw new IllegalArgumentException("User ID and reason are required");
        }
        return reportService
                .reportUser(request, reportRequest.getUserId(), reportRequest.getReason())
                .getBody();
    }

    public void blockUser(UUID blockedId, HttpServletRequest request) {
        UUID blockerId = getSessionUser(request).getId();
        blockService.blockUser(blockerId, blockedId);
    }

    public void unblockUser(UUID blockedId, HttpServletRequest request) {
        UUID blockerId = getSessionUser(request).getId();
        blockService.unblockUser(blockerId, blockedId);
    }

    public boolean isUserBlocked(UUID blockedId, HttpServletRequest request) {
        UUID blockerId = getSessionUser(request).getId();
        return blockService.isUserBlocked(blockerId, blockedId);
    }

    public boolean isBlockedByUser(UUID blockerId, HttpServletRequest request) {
        UUID currentUserId = getSessionUser(request).getId();
        return blockService.isUserBlocked(blockerId, currentUserId);
    }

    public void updateBlockchainConsent(boolean consent, HttpServletRequest request) {
        User user = getSessionUser(request);
        setConsent(consent, user);
    }

    public void setConsent(boolean consent, User user) {
        boolean previousConsent = user.isBlockchainConsent();
        user.setBlockchainConsent(consent);
        userRepository.save(user);

        blockchainService.recordDIDEvent(user, EventType.USER_REGISTERED, modelMapper.map(user, UserResponseDTO.class));

        activityService.logActivity(
                user,
                ActivityType.CONSENT,
                "Data sharing preferences updated",
                false,
                "Blockchain consent changed from " + previousConsent + " to " + consent);
    }

    public List<String> getUserRoles(UUID userId) {
        User user = getUserById(userId);
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        final List<String> orderedRoles = List.of(
                RoleType.VERIFIED.getValue(),
                RoleType.ANONYMOUS.getValue(),
                RoleType.ADMIN.getValue(),
                RoleType.USER.getValue());

        roles.sort(Comparator.comparing(role -> {
            int index = orderedRoles.indexOf(role);
            return index >= 0 ? index : Integer.MAX_VALUE;
        }));

        return roles;
    }

    public List<ActivityResponseDTO> getUserActivities(String type, HttpServletRequest request) {
        User user = getSessionUser(request);
        return activityService.getUserActivities(type, user);
    }

    public List<UserSearchDTO> searchUsers(String query, String currentUserId) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        List<User> users = userRepository.findTop10ByUsernameContainingIgnoreCase(query.trim());

        return users.stream()
                .filter(user -> !user.getId().toString().equals(currentUserId))
                .map(user -> new UserSearchDTO(user.getId(), user.getUsername()))
                .collect(Collectors.toList());
    }
}
