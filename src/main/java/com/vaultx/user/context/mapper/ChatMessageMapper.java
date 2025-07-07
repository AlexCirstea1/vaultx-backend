package com.vaultx.user.context.mapper;

import com.vaultx.user.context.model.file.ChatFile;
import com.vaultx.user.context.model.messaging.ChatMessage;
import com.vaultx.user.context.model.messaging.MessageType;
import com.vaultx.user.context.model.messaging.dto.ChatMessageDTO;
import com.vaultx.user.context.model.file.FileInfo;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatMessageMapper {
    @Mapping(target = "sender", expression = "java(entity.getSender().getId().toString())")
    @Mapping(target = "recipient", expression = "java(entity.getRecipient().getId().toString())")
    @Mapping(target = "file", expression = "java(mapFileInfo(entity.getFile()))")
    @Mapping(target = "type", expression = "java(determineMessageType(entity))")
    ChatMessageDTO toDto(ChatMessage entity);

    @Named("withType")
    @Mapping(target = "sender",    expression = "java(entity.getSender().getId().toString())")
    @Mapping(target = "recipient", expression = "java(entity.getRecipient().getId().toString())")
    @Mapping(target = "file",      expression = "java(mapFileInfo(entity.getFile()))")
    @Mapping(target = "type",       source = "type")
    ChatMessageDTO toDtoWithType(ChatMessage entity, String type);

    @BeanMapping(ignoreByDefault = false)
    ChatMessageDTO clone(ChatMessageDTO source);

    default FileInfo mapFileInfo(ChatFile cf) {
        if (cf == null) return null;
        return FileInfo.builder()
                .fileId(cf.getId())
                .fileName(cf.getFileName())
                .mimeType(cf.getMimeType())
                .sizeBytes(cf.getSizeBytes())
                .build();
    }

    default String determineMessageType(ChatMessage entity) {
        if (entity.getMessageType() == MessageType.FILE) {
            return "FILE_MESSAGE";
        }
        return "TEXT_MESSAGE";
    }
}
