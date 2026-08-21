package com.drawgame.chat.grpc;

import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface ChatGrpcMapper {

    @Mapping(target = "createdAtEpochMs", source = "createdAt", qualifiedByName = "instantToEpochMs")
    @Mapping(target = "type", source = "type", qualifiedByName = "typeToString")
    ChatMessageResponse toResponse(ChatMessage domain);

    @Named("instantToEpochMs")
    default long instantToEpochMs(Instant instant) {
        return instant != null ? instant.toEpochMilli() : 0L;
    }

    @Named("typeToString")
    default String typeToString(Enum<?> type) {
        return type != null ? type.name() : "USER";
    }
}
