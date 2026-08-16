package com.drawgame.room.grpc;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-17T00:17:05+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Red Hat, Inc.)"
)
@Component
public class RoomGrpcMapperImpl implements RoomGrpcMapper {

    @Override
    public void mapRoom(Room room, RoomResponse.Builder builder) {
        if ( room == null ) {
            return;
        }

        builder.setRoomId( room.id() );
        builder.setName( room.name() );
        builder.setHostId( room.hostId() );
        builder.setMaxPlayers( room.maxPlayers() );
        builder.setRoundCount( room.roundCount() );
        builder.setRoundDuration( room.roundDuration() );

        builder.setStatus( room.status() != null ? room.status().name() : "" );
    }

    @Override
    public PlayerMessage toPlayerMessage(RoomPlayer player) {
        if ( player == null ) {
            return null;
        }

        PlayerMessage.Builder playerMessage = PlayerMessage.newBuilder();

        playerMessage.setPlayerId( player.playerId() );
        playerMessage.setUsername( player.username() );

        return playerMessage.build();
    }
}
