package com.drawgame.room.grpc;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RoomGrpcMapper {

    default RoomResponse toResponse(Room room) {
        if (room == null) {
            return null;
        }

        RoomResponse.Builder builder = RoomResponse.newBuilder();
        mapRoom(room, builder);

        if (room.players() != null) {
            room.players()
                    .stream()
                    .map(this::toPlayerMessage)
                    .forEach(builder::addPlayers);
        }

        return builder.build();
    }

    @Mapping(target = "roomId", source = "id")
    @Mapping(target = "status", expression = "java(room.status() != null ? room.status().name() : \"\")")
    @Mapping(target = "playersList", ignore = true)
    void mapRoom(Room room, @MappingTarget RoomResponse.Builder builder);

    PlayerMessage toPlayerMessage(RoomPlayer player);
}
