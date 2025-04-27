package com.vaultx.user.context.mapper;

import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.user.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatMessageMapper {

    @Mapping(target = "sender", expression = "java(entity.getSender().getId().toString())")
    @Mapping(target = "recipient", expression = "java(entity.getRecipient().getId().toString())")
    ChatMessageDTO toDto(ChatMessage entity);

    @Named("withType")
    @Mapping(target = "sender", expression = "java(entity.getSender().getId().toString())")
    @Mapping(target = "recipient", expression = "java(entity.getRecipient().getId().toString())")
    @Mapping(target = "type", source = "type")
    ChatMessageDTO toDtoWithType(ChatMessage entity, String type);

    @BeanMapping(ignoreByDefault = false)
    ChatMessageDTO clone(ChatMessageDTO source);

    // Custom method that will be implemented in the implementation class
    default ChatMessage createEntity(ChatMessageDTO dto, User sender, User recipient) {
        if (dto == null) {
            return null;
        }

        return ChatMessage.builder()
                .sender(sender)
                .recipient(recipient)
                .ciphertext(dto.getCiphertext())
                .encryptedKeyForRecipient(dto.getEncryptedKeyForRecipient())
                .encryptedKeyForSender(dto.getEncryptedKeyForSender())
                .iv(dto.getIv())
                .senderKeyVersion(dto.getSenderKeyVersion())
                .recipientKeyVersion(dto.getRecipientKeyVersion())
                .timestamp(dto.getTimestamp())
                .isRead(dto.isRead())
                .oneTime(dto.isOneTime())
                .build();
    }
}
