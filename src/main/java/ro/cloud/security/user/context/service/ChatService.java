package ro.cloud.security.user.context.service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.repository.ChatMessageRepository;

@Service
@AllArgsConstructor
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void markMessagesAsRead(List<UUID> messageIds) {
        chatMessageRepository.markMessagesAsRead(messageIds);
    }
}
