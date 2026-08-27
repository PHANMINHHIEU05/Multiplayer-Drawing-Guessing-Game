package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates that an incoming {@link DrawingMessage} is authorized to be broadcast.
 *
 * <p>Checks (in order):
 * <ol>
 *   <li>Session has a bound roomId</li>
 *   <li>Session has a bound playerId</li>
 *   <li>An active game state is cached for the room</li>
 *   <li>Game status is PLAYING</li>
 *   <li>The sender is the current drawer</li>
 *   <li>The message round matches the current round</li>
 * </ol>
 *
 * <p>All rejections are logged at DEBUG level — do not spam INFO for every drawing frame.
 */
@Component
public class DrawingAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DrawingAuthorizationService.class);

    private final DrawingRoomStateCache cache;

    public DrawingAuthorizationService(DrawingRoomStateCache cache) {
        this.cache = cache;
    }

    /** Result of an authorization check. */
    public sealed interface AuthResult permits AuthResult.Authorized, AuthResult.Rejected {

        record Authorized() implements AuthResult {}

        record Rejected(String reason) implements AuthResult {}

        static AuthResult ok() { return new Authorized(); }
        static AuthResult reject(String reason) { return new Rejected(reason); }

        default boolean isAuthorized() { return this instanceof Authorized; }
    }

    /**
     * Authorize a drawing message for the given session context.
     *
     * @param session session context resolved from the WebSocket connection
     * @param message the incoming drawing message
     * @return {@link AuthResult.Authorized} if all checks pass, {@link AuthResult.Rejected} otherwise
     */
    public AuthResult authorize(DrawingSessionContext session, DrawingMessage message) {
        // 1. Must have a bound room
        if (session.roomId() == null || session.roomId().isBlank()) {
            log.debug("Drawing rejected — session {} has no bound roomId", session.sessionId());
            return AuthResult.reject("NO_ROOM");
        }

        // 2. Must have a bound player
        if (session.playerId() == null || session.playerId().isBlank()) {
            log.debug("Drawing rejected — session {} has no bound playerId", session.sessionId());
            return AuthResult.reject("NO_PLAYER");
        }

        // 3. Must have an active game state cached
        Optional<DrawingRoomState> stateOpt = cache.get(session.roomId());
        if (stateOpt.isEmpty()) {
            log.debug("Drawing rejected — no cached game state for room={}, session={}",
                    session.roomId(), session.sessionId());
            return AuthResult.reject("GAME_NOT_ACTIVE");
        }

        DrawingRoomState state = stateOpt.get();

        // 4. Game must be in PLAYING status
        if (!state.isPlaying()) {
            log.debug("Drawing rejected — game not PLAYING for room={} (status={}), session={}",
                    session.roomId(), state.gameStatus(), session.sessionId());
            return AuthResult.reject("GAME_NOT_PLAYING");
        }

        // 5. Sender must be the current drawer
        if (!session.playerId().equals(state.currentDrawerId())) {
            log.debug("Drawing rejected — player={} is not the current drawer={} in room={}",
                    session.playerId(), state.currentDrawerId(), session.roomId());
            return AuthResult.reject("NOT_DRAWER");
        }

        // 6. Message round must match current round
        if (message.round() != state.currentRound()) {
            log.debug("Drawing rejected — message round={} != currentRound={} in room={}",
                    message.round(), state.currentRound(), session.roomId());
            return AuthResult.reject("WRONG_ROUND");
        }

        return AuthResult.ok();
    }
}
