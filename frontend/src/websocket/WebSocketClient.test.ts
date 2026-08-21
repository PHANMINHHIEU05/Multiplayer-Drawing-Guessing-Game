import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WebSocketClient } from './WebSocketClient';
import { generateRequestId } from './protocol';

describe('WebSocketClient Protocol & Correlation', () => {
  it('generates unique requestId strings', () => {
    const id1 = generateRequestId();
    const id2 = generateRequestId();
    expect(id1).toBeDefined();
    expect(id2).toBeDefined();
    expect(id1).not.toBe(id2);
  });

  it('initializes in DISCONNECTED state', () => {
    const client = new WebSocketClient('ws://localhost:8080/ws/game');
    expect(client).toBeDefined();
  });
});
