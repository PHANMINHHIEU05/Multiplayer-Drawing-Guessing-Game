import { WSRequest, WSResponse, MessageHandler } from './messageTypes';
import { createWSRequest } from './protocol';
import { connectionStore } from '../store/connectionStore';

interface PendingRequest {
  resolve: (value: WSResponse | PromiseLike<WSResponse>) => void;
  reject: (reason?: any) => void;
  timer: any;
}

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private pendingRequests = new Map<string, PendingRequest>();
  private messageListeners = new Set<MessageHandler>();
  private reconnectAttempts = 0;
  private maxReconnectDelay = 16000;
  private reconnectTimer: any = null;
  private isIntentionallyClosed = false;

  constructor(url?: string) {
    this.url = url || (import.meta as any).env?.VITE_WS_URL || 'ws://localhost:8080/ws/game';
  }

  public connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isIntentionallyClosed = false;
    connectionStore.setStatus(this.reconnectAttempts > 0 ? 'RECONNECTING' : 'CONNECTING');

    try {
      this.ws = new WebSocket(this.url);

      this.ws.onopen = () => {
        console.log('[WebSocket] Connected to', this.url);
        this.reconnectAttempts = 0;
        connectionStore.setStatus('CONNECTED');
      };

      this.ws.onmessage = (event) => {
        this.handleIncomingMessage(event.data);
      };

      this.ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error);
        connectionStore.setLastError('Connection error encountered');
      };

      this.ws.onclose = (event) => {
        console.log('[WebSocket] Disconnected code:', event.code, 'reason:', event.reason);
        this.ws = null;
        this.rejectAllPending('WebSocket connection closed');

        if (!this.isIntentionallyClosed) {
          connectionStore.setStatus('DISCONNECTED');
          this.scheduleReconnect();
        } else {
          connectionStore.setStatus('DISCONNECTED');
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
  }

  public send<T = any>(type: string, payload: T, timeoutMs: number = 10000): Promise<WSResponse> {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        return reject(new Error('WebSocket is not connected'));
      }

      const req: WSRequest<T> = createWSRequest(type, payload);

      const timer = setTimeout(() => {
        if (this.pendingRequests.has(req.requestId)) {
          this.pendingRequests.delete(req.requestId);
          reject(new Error(`Request timeout for '${type}' (reqId: ${req.requestId})`));
        }
      }, timeoutMs);

      this.pendingRequests.set(req.requestId, { resolve, reject, timer });

      try {
        this.ws.send(JSON.stringify(req));
      } catch (err) {
        clearTimeout(timer);
        this.pendingRequests.delete(req.requestId);
        reject(err);
      }
    });
  }

  public addMessageListener(listener: MessageHandler): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  private handleIncomingMessage(raw: string): void {
    try {
      const response: WSResponse = JSON.parse(raw);
      console.log('[WebSocket] Inbound message:', response);

      // Check correlation ID
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

  private scheduleReconnect(): void {
    if (this.isIntentionallyClosed || this.reconnectTimer) return;

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), this.maxReconnectDelay);
    console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt #${this.reconnectAttempts})...`);
    connectionStore.setStatus('RECONNECTING');

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
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
