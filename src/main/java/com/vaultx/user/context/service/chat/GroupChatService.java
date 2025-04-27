package com.vaultx.user.context.service.chat;

import com.vaultx.user.context.model.blockchain.EventType;
import com.vaultx.user.context.model.messaging.GroupChat;
import com.vaultx.user.context.model.messaging.GroupChatMessage;
import com.vaultx.user.context.model.messaging.dto.GroupChatHistoryDTO;
import com.vaultx.user.context.model.messaging.dto.GroupChatMessageDTO;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.GroupChatMessageRepository;
import com.vaultx.user.context.repository.GroupChatRepository;
import com.vaultx.user.context.service.user.BlockchainService;
import com.vaultx.user.context.service.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class GroupChatService {

    private final GroupChatRepository groupChatRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelMapper modelMapper;
    private final UserService userService;
    private final BlockchainService blockchainService;

    /**
     * Creates a new group chat with the given name and participants (UUID strings).
     */
    public GroupChat createGroupChat(String groupName, List<String> participantIds) {
        // Convert each participant ID string to a User entity
        Set<User> participantUsers = participantIds.stream()
                .map(idStr -> {
                    UUID userUuid = UUID.fromString(idStr);
                    return userService.getUserById(userUuid);
                })
                .collect(Collectors.toSet());

        // 1) Create and save the group
        GroupChat groupChat = GroupChat.builder()
                .groupName(groupName)
                .participants(participantUsers)
                .createdAt(Instant.now())
                .build();
        groupChat = groupChatRepository.save(groupChat);

        // 2) Record a pairing event for each user
        for (User user : participantUsers) {
            blockchainService.recordDIDEvent(user, EventType.CHAT_CREATED, groupChat);
        }
        log.info("Recorded group pairing event on blockchain for group: {}", groupChat.getId());

        return groupChat;
    }

    /**
     * Adds a participant (string ID) to an existing group chat.
     */
    public GroupChat addParticipant(UUID groupId, String participantIdStr) {
        Optional<GroupChat> optionalGroup = groupChatRepository.findById(groupId);
        if (optionalGroup.isEmpty()) {
            throw new RuntimeException("Group not found");
        }

        GroupChat groupChat = optionalGroup.get();
        UUID participantUuid = UUID.fromString(participantIdStr);
        User participantUser = userService.getUserById(participantUuid);

        groupChat.getParticipants().add(participantUser);
        groupChat.setUpdatedAt(Instant.now());
        return groupChatRepository.save(groupChat);
    }

    /**
     * Removes a participant from a group chat.
     */
    public GroupChat removeParticipant(UUID groupId, String participantIdStr) {
        Optional<GroupChat> optionalGroup = groupChatRepository.findById(groupId);
        if (optionalGroup.isEmpty()) {
            throw new RuntimeException("Group not found");
        }

        GroupChat groupChat = optionalGroup.get();
        UUID participantUuid = UUID.fromString(participantIdStr);
        User participantUser = userService.getUserById(participantUuid);

        groupChat.getParticipants().remove(participantUser);
        groupChat.setUpdatedAt(Instant.now());
        return groupChatRepository.save(groupChat);
    }

    /**
     * Sends a group chat message. The message is saved and then broadcast
     * to a topic specific to the group.
     */
    public void sendGroupMessage(GroupChatMessageDTO messageDTO) {
        // 1) Fetch the group
        UUID groupId = messageDTO.getGroupId();
        GroupChat group =
                groupChatRepository.findById(groupId).orElseThrow(() -> new RuntimeException("Group not found"));

        // 2) Fetch the sender as a User
        UUID senderUuid = UUID.fromString(messageDTO.getSender());
        User senderUser = userService.getUserById(senderUuid);

        // 3) Build and save the GroupChatMessage
        GroupChatMessage message = GroupChatMessage.builder()
                .group(group)
                .sender(senderUser)
                .content(messageDTO.getContent())
                .timestamp(Instant.now())
                .build();

        message = groupChatMessageRepository.save(message);

        // 4) Convert to DTO for broadcasting
        GroupChatMessageDTO outgoing = modelMapper.map(message, GroupChatMessageDTO.class);
        // We'll set groupId and sender as strings to match your existing DTO pattern
        outgoing.setGroupId(groupId);
        outgoing.setSender(senderUuid.toString());

        // 5) Broadcast to all subscribers of the group topic
        messagingTemplate.convertAndSend("/topic/group/" + groupId, outgoing);
    }

    /**
     * Retrieves the history of messages for a given group chat.
     */
    public GroupChatHistoryDTO getGroupChatHistory(UUID groupId) {
        // 1) Find all messages for that group, ordered by timestamp
        List<GroupChatMessage> messages = groupChatMessageRepository.findByGroupIdOrderByTimestampAsc(groupId);

        // 2) Convert each entity to a GroupChatMessageDTO
        List<GroupChatMessageDTO> messageDTOs = messages.stream()
                .map(m -> {
                    GroupChatMessageDTO dto = modelMapper.map(m, GroupChatMessageDTO.class);
                    dto.setGroupId(groupId);
                    // Convert the sender user ID to string
                    dto.setSender(m.getSender().getId().toString());
                    return dto;
                })
                .collect(Collectors.toList());

        // 3) Retrieve the group name (if found)
        GroupChat group = groupChatRepository.findById(groupId).orElse(null);
        String groupName = (group != null) ? group.getGroupName() : "Unknown";

        // 4) Build the history DTO
        return GroupChatHistoryDTO.builder()
                .groupId(groupId)
                .groupName(groupName)
                .messages(messageDTOs)
                .build();
    }
}
