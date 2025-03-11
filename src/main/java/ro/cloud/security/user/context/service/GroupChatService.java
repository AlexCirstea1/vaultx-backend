package ro.cloud.security.user.context.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.messaging.GroupChat;
import ro.cloud.security.user.context.model.messaging.GroupChatMessage;
import ro.cloud.security.user.context.model.messaging.dto.GroupChatMessageDTO;
import ro.cloud.security.user.context.model.messaging.dto.GroupChatHistoryDTO;
import ro.cloud.security.user.context.repository.GroupChatMessageRepository;
import ro.cloud.security.user.context.repository.GroupChatRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class GroupChatService {

    private final GroupChatRepository groupChatRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelMapper modelMapper;

    /**
     * Creates a new group chat with the given name and participants.
     */
    public GroupChat createGroupChat(String groupName, List<String> participantIds) {
        GroupChat groupChat = GroupChat.builder()
                .groupName(groupName)
                .participantIds(new HashSet<>(participantIds))
                .createdAt(Instant.now())
                .build();
        return groupChatRepository.save(groupChat);
    }

    /**
     * Adds a participant to an existing group chat.
     */
    public GroupChat addParticipant(UUID groupId, String participantId) {
        Optional<GroupChat> optionalGroup = groupChatRepository.findById(groupId);
        if (optionalGroup.isPresent()) {
            GroupChat groupChat = optionalGroup.get();
            groupChat.getParticipantIds().add(participantId);
            groupChat.setUpdatedAt(Instant.now());
            return groupChatRepository.save(groupChat);
        }
        throw new RuntimeException("Group not found");
    }

    /**
     * Removes a participant from a group chat.
     */
    public GroupChat removeParticipant(UUID groupId, String participantId) {
        Optional<GroupChat> optionalGroup = groupChatRepository.findById(groupId);
        if (optionalGroup.isPresent()) {
            GroupChat groupChat = optionalGroup.get();
            groupChat.getParticipantIds().remove(participantId);
            groupChat.setUpdatedAt(Instant.now());
            return groupChatRepository.save(groupChat);
        }
        throw new RuntimeException("Group not found");
    }

    /**
     * Sends a group chat message. The message is saved and then broadcast
     * to a topic specific to the group.
     */
    public void sendGroupMessage(GroupChatMessageDTO messageDTO) {
        GroupChatMessage message = GroupChatMessage.builder()
                .groupId(messageDTO.getGroupId())
                .sender(messageDTO.getSender())
                .content(messageDTO.getContent())
                .timestamp(Instant.now())
                .build();
        message = groupChatMessageRepository.save(message);
        GroupChatMessageDTO outgoing = modelMapper.map(message, GroupChatMessageDTO.class);
        // Broadcast to all subscribers of the group topic
        messagingTemplate.convertAndSend("/topic/group/" + messageDTO.getGroupId(), outgoing);
    }

    /**
     * Retrieves the history of messages for a given group chat.
     */
    public GroupChatHistoryDTO getGroupChatHistory(UUID groupId) {
        List<GroupChatMessage> messages = groupChatMessageRepository.findByGroupIdOrderByTimestampAsc(groupId);
        List<GroupChatMessageDTO> messageDTOs = messages.stream()
                .map(m -> modelMapper.map(m, GroupChatMessageDTO.class))
                .collect(Collectors.toList());
        Optional<GroupChat> optionalGroup = groupChatRepository.findById(groupId);
        String groupName = optionalGroup.map(GroupChat::getGroupName).orElse("Unknown");
        return GroupChatHistoryDTO.builder()
                .groupId(groupId)
                .groupName(groupName)
                .messages(messageDTOs)
                .build();
    }
}
