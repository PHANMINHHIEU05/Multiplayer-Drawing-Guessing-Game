import { WSRequest, WSResponse, MessageHandler } from './messageTypes';
import { createWSRequest, MessageType } from './protocol';
import { connectionStore } from '../store/connectionStore';
import { metricsStore } from '../store/metricsStore';
import { roomStore } from '../store/roomStore';
import { playerStore } from '../store/playerStore';

interface PendingRequest {
  resolve: (value: WSResponse | PromiseLike<WSResponse>) => void;
  reject: (reason?: any) => void;
  timer: any;
}

export type BinaryMessageHandler = (buffer: ArrayBuffer) => void;

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private pendingRequests = new Map<string, PendingRequest>();
  private messageListeners = new Set<MessageHandler>();
  private binaryListeners = new Set<BinaryMessageHandler>();
  private reconnectAttempts = 0;
  private baseReconnectDelay = 1000;
  private maxReconnectDelay = 10000;
  private reconnectTimer: any = null;
  private isIntentionallyClosed = false;

  // Heartbeat Telemetry (TV4)
  private heartbeatIntervalMs = 2000;
  private heartbeatTimer: any = null;
  private rateTickTimer: any = null;
  private lastPingSentAt: number = 0;
  private missedPongsCount = 0;
  private maxMissedPongs = 3;

  constructor(url?: string) {
    this.url = url || (import.meta as any).env?.VITE_WS_URL || 'ws://localhost:8080/ws';
    this.startRateTicker();
  }

  public connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isIntentionallyClosed = false;
    const isReconnecting = this.reconnectAttempts > 0;
    connectionStore.setStatus(isReconnecting ? 'RECONNECTING' : 'CONNECTING');
    metricsStore.setStatus(isReconnecting ? 'RECONNECTING' : 'CONNECTING');

    try {
      this.ws = new WebSocket(this.url);
      this.ws.binaryType = 'arraybuffer';

      this.ws.onopen = () => {
        const wasReconnecting = this.reconnectAttempts > 0;
        console.log('[WebSocket] Connected to', this.url, wasReconnecting ? '(Reconnected)' : '');
        this.reconnectAttempts = 0;
        this.missedPongsCount = 0;
        connectionStore.setStatus('CONNECTED');
        metricsStore.setStatus('CONNECTED');

        this.startHeartbeat();

        if (wasReconnecting) {
          this.restoreStateAfterReconnect();
        }
      };

      this.ws.onmessage = (event) => {
        const data = event.data;
        if (data instanceof ArrayBuffer) {
          metricsStore.recordRx(data.byteLength);
        } else if (typeof data === 'string') {
          metricsStore.recordRx(data.length);
        }
        this.handleIncomingMessage(data);
      };

      this.ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error);
        connectionStore.setLastError('Connection error encountered');
      };

      this.ws.onclose = (event) => {
        console.log('[WebSocket] Disconnected code:', event.code, 'reason:', event.reason);
        this.ws = null;
        this.stopHeartbeat();
        this.rejectAllPending('WebSocket connection closed');

        if (!this.isIntentionallyClosed) {
          connectionStore.setStatus('DISCONNECTED');
          metricsStore.setStatus('DISCONNECTED');
          this.scheduleReconnect();
        } else {
          connectionStore.setStatus('DISCONNECTED');
          metricsStore.setStatus('DISCONNECTED');
        }
      };
    } catch (err: any) {
      console.error('[WebSocket] Connect exception:', err);
      connectionStore.setLastError(err.message || 'Failed to connect');
      this.scheduleReconnect();
    }
  }

  public disconnect(): void {
    this.isIntentionallyClosed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.rejectAllPending('Client disconnected');
    connectionStore.setStatus('DISCONNECTED');
    metricsStore.setStatus('DISCONNECTED');
  }

  public send<T = any>(type: string, payload: T, timeoutMs: number = 10000): Promise<WSResponse> {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        return reject(new Error('WebSocket is not connected'));
      }

      const req: WSRequest<T> = createWSRequest(type, payload);
      const raw = JSON.stringify(req);

      const timer = setTimeout(() => {
        if (this.pendingRequests.has(req.requestId)) {
          this.pendingRequests.delete(req.requestId);
          reject(new Error(`Request timeout for '${type}' (reqId: ${req.requestId})`));
        }
      }, timeoutMs);

      this.pendingRequests.set(req.requestId, { resolve, reject, timer });

      try {
        metricsStore.recordTx(raw.length);
        this.ws.send(raw);
      } catch (err) {
        clearTimeout(timer);
        this.pendingRequests.delete(req.requestId);
        reject(err);
      }
    });
  }

  /** Fire-and-forget raw JSON string send (no promise / timeout overhead) */
  public sendRaw(data: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    metricsStore.recordTx(data.length);
    this.ws.send(data);
  }

  /** Fire-and-forget binary ArrayBuffer send for high frequency drawing */
  public sendBinary(data: ArrayBuffer | ArrayBufferView): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    const byteLength = 'byteLength' in data ? data.byteLength : (data as ArrayBuffer).byteLength;
    metricsStore.recordTx(byteLength);
    this.ws.send(data);
  }

  public addMessageListener(listener: MessageHandler): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  public addBinaryListener(listener: BinaryMessageHandler): () => void {
    this.binaryListeners.add(listener);
    return () => this.binaryListeners.delete(listener);
  }

  private handleIncomingMessage(raw: string | ArrayBuffer): void {
    if (raw instanceof ArrayBuffer) {
      this.binaryListeners.forEach((listener) => {
        try {
          listener(raw);
        } catch (err) {
          console.error('[WebSocket] Binary listener error:', err);
        }
      });
      return;
    }

    try {
      const response: WSResponse = JSON.parse(raw);

      // Heartbeat PONG interceptor (TV4)
      if (response.type === 'APP_PONG' || response.type === 'PONG') {
        const clientTimestamp = response.clientTimestamp || response.sentAt || this.lastPingSentAt;
        const serverTimestamp = response.serverTimestamp;
        const queueSize = response.queueSize;
        const gatewayId = response.gatewayId;
        this.missedPongsCount = 0;
        metricsStore.recordHeartbeatPong(clientTimestamp, serverTimestamp, queueSize, gatewayId);
        return;
      }

      // Correlation ID resolution
      if (response.requestId && this.pendingRequests.has(response.requestId)) {
        const pending = this.pendingRequests.get(response.requestId)!;
        clearTimeout(pending.timer);
        this.pendingRequests.delete(response.requestId);

        if (response.type === 'ERROR') {
          const errMsg = response.message || response.error?.message || 'Gateway error';
          pending.reject(new Error(errMsg));
        } else {
          pending.resolve(response);
        }
      }

      // Dispatch to generic message listeners
      this.messageListeners.forEach((listener) => {
        try {
          listener(response);
        } catch (err) {
          console.error('[WebSocket] Listener error:', err);
        }
      });
    } catch (err) {
      console.error('[WebSocket] Failed to parse message:', raw, err);
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

      this.missedPongsCount++;
      if (this.missedPongsCount > this.maxMissedPongs) {
        console.warn(`[WebSocket] Heartbeat timeout (${this.missedPongsCount} missed pongs). Reconnecting...`);
        metricsStore.incrementHeartbeatTimeout();
        this.ws.close();
        return;
      }

      this.lastPingSentAt = Date.now();
      const pingMsg = JSON.stringify({
        type: 'APP_PING',
        timestamp: this.lastPingSentAt,
      });
      this.sendRaw(pingMsg);
    }, this.heartbeatIntervalMs);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private startRateTicker(): void {
    if (this.rateTickTimer) return;
    this.rateTickTimer = setInterval(() => {
      metricsStore.tickRates();
    }, 1000);
  }

  private scheduleReconnect(): void {
    if (this.isIntentionallyClosed || this.reconnectTimer) return;

    this.reconnectAttempts++;
    metricsStore.incrementReconnect();

    // Exponential backoff with ±20% jitter
    const backoff = Math.min(
      this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
      this.maxReconnectDelay
    );
    const jitter = 0.8 + Math.random() * 0.4;
    const delay = Math.round(backoff * jitter);

    console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt #${this.reconnectAttempts})...`);
    connectionStore.setStatus('RECONNECTING');
    metricsStore.setStatus('RECONNECTING');

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  /** Restore room and game context after reconnecting */
  private restoreStateAfterReconnect(): void {
    const { playerId, username } = playerStore.getState();
    const currentRoom = roomStore.getState().room;

    if (currentRoom && currentRoom.roomId && playerId) {
      console.log(`[WebSocket] Restoring session in room ${currentRoom.roomId}...`);
      // Re-join/re-bind room on gateway
      this.send(MessageType.JOIN_ROOM, {
        roomId: currentRoom.roomId,
        playerId,
        username: username || `Player-${playerId}`,
      }, 5000)
        .then(() => {
          console.log('[WebSocket] Room context restored successfully');
          // Fetch latest game state if game is active
          if (currentRoom.status === 'IN_GAME') {
            return this.send(MessageType.GET_GAME_STATE, {
              roomId: currentRoom.roomId,
              playerId,
            }, 5000);
          }
        })
        .catch((err) => {
          console.warn('[WebSocket] Failed to restore room/game state:', err);
        });
    }
  }

  private rejectAllPending(reason: string): void {
    this.pendingRequests.forEach((pending) => {
      clearTimeout(pending.timer);
      pending.reject(new Error(reason));
    });
    this.pendingRequests.clear();
  }
}

export const wsClient = new WebSocketClient();
