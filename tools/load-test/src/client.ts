import WebSocket from 'ws';

export interface ClientMetrics {
  id: string;
  connected: boolean;
  txMessages: number;
  rxMessages: number;
  txBytes: number;
  rxBytes: number;
  rttSamples: number[];
  avgRttMs: number;
  p95RttMs: number;
  sequenceGaps: number;
}

export class LoadTestClient {
  public id: string;
  public url: string;
  private ws: WebSocket | null = null;
  public connected = false;

  public txMessages = 0;
  public rxMessages = 0;
  public txBytes = 0;
  public rxBytes = 0;
  public rttSamples: number[] = [];
  public sequenceGaps = 0;

  private pendingPings = new Map<number, (rtt: number) => void>();
  private strokeSeqMap = new Map<string, number>();
  public simulatedProcessingDelayMs = 0;

  constructor(id: string, url: string = 'ws://localhost:8080/ws') {
    this.id = id;
    this.url = url;
  }

  public connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.url);
        this.ws.binaryType = 'nodebuffer';

        this.ws.on('open', () => {
          this.connected = true;
          resolve();
        });

        this.ws.on('message', async (data: WebSocket.Data) => {
          let byteLen = 0;
          if (Buffer.isBuffer(data)) {
            byteLen = data.length;
            this.handleBinaryMessage(data);
          } else if (typeof data === 'string') {
            byteLen = Buffer.byteLength(data, 'utf8');
            this.handleTextMessage(data);
          }

          this.rxMessages++;
          this.rxBytes += byteLen;

          if (this.simulatedProcessingDelayMs > 0) {
            await new Promise((r) => setTimeout(r, this.simulatedProcessingDelayMs));
          }
        });

        this.ws.on('error', (err) => {
          this.connected = false;
          reject(err);
        });

        this.ws.on('close', () => {
          this.connected = false;
        });
      } catch (e) {
        this.connected = false;
        reject(e);
      }
    });
  }

  public sendJson(type: string, payload: any, reqId?: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    const msg = JSON.stringify({
      type,
      payload,
      requestId: reqId || `req_${Date.now()}_${Math.random()}`,
    });
    const bytes = Buffer.byteLength(msg, 'utf8');
    this.txMessages++;
    this.txBytes += bytes;
    this.ws.send(msg);
  }

  public sendBinary(buffer: Buffer): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    this.txMessages++;
    this.txBytes += buffer.length;
    this.ws.send(buffer);
  }

  public sendPing(): Promise<number> {
    return new Promise((resolve) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        return resolve(-1);
      }
      const sentAt = Date.now();
      this.pendingPings.set(sentAt, resolve);
      this.sendJson('APP_PING', { sentAt });
    });
  }

  private handleTextMessage(text: string): void {
    try {
      const parsed = JSON.parse(text);
      if (parsed.type === 'APP_PONG' || parsed.type === 'PONG') {
        const clientTimestamp = parsed.clientTimestamp || parsed.payload?.sentAt;
        if (clientTimestamp && this.pendingPings.has(clientTimestamp)) {
          const rtt = Math.max(0, Date.now() - clientTimestamp);
          this.rttSamples.push(rtt);
          const cb = this.pendingPings.get(clientTimestamp);
          this.pendingPings.delete(clientTimestamp);
          if (cb) cb(rtt);
        }
      }

      if (parsed.type === 'DRAW_BATCH_EVENT' && parsed.strokeId && parsed.seqStart !== undefined) {
        const expected = this.strokeSeqMap.get(parsed.strokeId);
        const count = Array.isArray(parsed.points) ? parsed.points.length : 1;
        if (expected !== undefined && parsed.seqStart > expected) {
          this.sequenceGaps += (parsed.seqStart - expected);
        }
        this.strokeSeqMap.set(parsed.strokeId, parsed.seqStart + count);
      }
    } catch {
      // Ignore
    }
  }

  private handleBinaryMessage(buf: Buffer): void {
    if (buf.length < 2) return;
    const opcode = buf.readUInt8(1);
    if (opcode === 0x02 && buf.length >= 26) {
      // DRAW_BATCH
      const seqStart = buf.readUInt32BE(4);
      const pointCount = buf.readUInt16BE(8);
      const strokeId = buf.subarray(10, 26).toString('hex');

      const expected = this.strokeSeqMap.get(strokeId);
      if (expected !== undefined && seqStart > expected) {
        this.sequenceGaps += (seqStart - expected);
      }
      this.strokeSeqMap.set(strokeId, seqStart + pointCount);
    }
  }

  public getMetrics(): ClientMetrics {
    const sorted = [...this.rttSamples].sort((a, b) => a - b);
    const sum = this.rttSamples.reduce((a, b) => a + b, 0);
    const avg = this.rttSamples.length > 0 ? Math.round((sum / this.rttSamples.length) * 10) / 10 : 0;
    const p95 = sorted.length > 0 ? sorted[Math.min(Math.round(0.95 * (sorted.length - 1)), sorted.length - 1)] : 0;

    return {
      id: this.id,
      connected: this.connected,
      txMessages: this.txMessages,
      rxMessages: this.rxMessages,
      txBytes: this.txBytes,
      rxBytes: this.rxBytes,
      rttSamples: this.rttSamples,
      avgRttMs: avg,
      p95RttMs: p95,
      sequenceGaps: this.sequenceGaps,
    };
  }

  public close(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.connected = false;
  }
}
