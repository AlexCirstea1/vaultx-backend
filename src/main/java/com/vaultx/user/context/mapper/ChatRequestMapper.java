package com.vaultx.user.context.mapper;

import com.vaultx.user.context.model.messaging.ChatRequest;
import com.vaultx.user.context.model.messaging.dto.ChatRequestDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatRequestMapper {

    @Mapping(target = "requester", expression = "java(e.getRequester().getId().toString())")
    @Mapping(target = "recipient", expression = "java(e.getRecipient().getId().toString())")
    @Mapping(target = "status", expression = "java(e.getStatus().name())")
    @Mapping(target = "timestamp", source = "createdAt")
    ChatRequestDTO toDto(ChatRequest e);
}
