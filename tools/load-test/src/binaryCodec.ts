/**
 * Binary Codec for Load Testing Harness (Matches backend BinaryDrawingCodec)
 */

export const DrawingOpcode = {
  DRAW_START: 0x01,
  DRAW_BATCH: 0x02,
  DRAW_END: 0x03,
  CLEAR_CANVAS: 0x04,
} as const;

export const PROTOCOL_VERSION = 1;
export const QUANTIZATION_FACTOR = 65535.0;

export interface NormalizedPoint {
  x: number;
  y: number;
}

export function encodeCoordinate(value: number): number {
  const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
  return Math.round(clamped * QUANTIZATION_FACTOR);
}

export function decodeCoordinate(encoded: number): number {
  return Math.max(0, Math.min(1, encoded / QUANTIZATION_FACTOR));
}

export function uuidToBytes(uuidStr: string): Uint8Array {
  const clean = uuidStr.replace(/-/g, '');
  const bytes = new Uint8Array(16);
  for (let i = 0; i < 16; i++) {
    bytes[i] = parseInt(clean.substring(i * 2, i * 2 + 2), 16) || 0;
  }
  return bytes;
}

export function generateStrokeId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function encodeDrawStart(round: number, strokeId: string, x: number, y: number, r = 0, g = 0, b = 0, width = 4): Buffer {
  const buffer = Buffer.alloc(28);
  buffer.writeUInt8(PROTOCOL_VERSION, 0);
  buffer.writeUInt8(DrawingOpcode.DRAW_START, 1);
  buffer.writeUInt16BE(round, 2);
  buffer.set(uuidToBytes(strokeId), 4);
  buffer.writeUInt16BE(encodeCoordinate(x), 20);
  buffer.writeUInt16BE(encodeCoordinate(y), 22);
  buffer.writeUInt8(r, 24);
  buffer.writeUInt8(g, 25);
  buffer.writeUInt8(b, 26);
  buffer.writeUInt8(width, 27);
  return buffer;
}

export function encodeDrawBatch(round: number, strokeId: string, seqStart: number, points: NormalizedPoint[]): Buffer {
  const pointCount = Math.min(points.length, 256);
  const totalSize = 26 + pointCount * 4;
  const buffer = Buffer.alloc(totalSize);

  buffer.writeUInt8(PROTOCOL_VERSION, 0);
  buffer.writeUInt8(DrawingOpcode.DRAW_BATCH, 1);
  buffer.writeUInt16BE(round, 2);
  buffer.writeUInt32BE(seqStart, 4);
  buffer.writeUInt16BE(pointCount, 8);
  buffer.set(uuidToBytes(strokeId), 10);

  let offset = 26;
  for (let i = 0; i < pointCount; i++) {
    buffer.writeUInt16BE(encodeCoordinate(points[i].x), offset);
    buffer.writeUInt16BE(encodeCoordinate(points[i].y), offset + 2);
    offset += 4;
  }
  return buffer;
}

export function encodeClearCanvas(round: number): Buffer {
  const buffer = Buffer.alloc(4);
  buffer.writeUInt8(PROTOCOL_VERSION, 0);
  buffer.writeUInt8(DrawingOpcode.CLEAR_CANVAS, 1);
  buffer.writeUInt16BE(round, 2);
  return buffer;
}
