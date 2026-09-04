/**
 * Binary Drawing Protocol Codec (v1.2.0)
 * Fully compatible with Backend Java BinaryDrawingEncoder & BinaryDrawingDecoder
 * 
 * Byte order: Big-Endian
 * Quantization: uint16 (0 - 65535) for normalized coordinates (0.0 - 1.0)
 */

export const DrawingOpcode = {
  DRAW_START: 0x01,
  DRAW_BATCH: 0x02,
  DRAW_END: 0x03,
  CLEAR_CANVAS: 0x04,
} as const;

export type DrawingOpcodeType = typeof DrawingOpcode[keyof typeof DrawingOpcode];

export const PROTOCOL_VERSION = 1;
export const QUANTIZATION_FACTOR = 65535.0;

export interface NormalizedPoint {
  x: number; // 0.0 - 1.0
  y: number; // 0.0 - 1.0
}

export interface DrawStartData {
  version?: number;
  round: number;
  strokeId: string; // UUID string
  x: number;        // 0.0 - 1.0
  y: number;        // 0.0 - 1.0
  colorHex: string; // e.g. "#EF4444"
  width: number;    // 1 - 64 px
  tool?: 'BRUSH' | 'ERASER';
}

export interface DrawBatchData {
  version?: number;
  round: number;
  strokeId: string; // UUID string
  seqStart: number; // uint32
  points: NormalizedPoint[];
}

export interface DrawEndData {
  version?: number;
  round: number;
  strokeId: string; // UUID string
}

export interface ClearCanvasData {
  version?: number;
  round: number;
}

export type DecodedDrawingMessage =
  | { type: 'DRAW_START'; data: DrawStartData }
  | { type: 'DRAW_BATCH'; data: DrawBatchData }
  | { type: 'DRAW_END'; data: DrawEndData }
  | { type: 'CLEAR_CANVAS'; data: ClearCanvasData };

// ─── Coordinate Helpers ──────────────────────────────────────────────

export function encodeCoordinate(value: number): number {
  const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
  return Math.round(clamped * QUANTIZATION_FACTOR);
}

export function decodeCoordinate(encoded: number): number {
  return Math.max(0, Math.min(1, encoded / QUANTIZATION_FACTOR));
}

// ─── Color Helpers ───────────────────────────────────────────────────

export function hexToRgb(hex: string): { r: number; g: number; b: number } {
  let cleanHex = hex.replace('#', '');
  if (cleanHex.length === 3) {
    cleanHex = cleanHex.split('').map((c) => c + c).join('');
  }
  const num = parseInt(cleanHex, 16);
  if (isNaN(num)) {
    return { r: 248, g: 250, b: 252 }; // Default light slate
  }
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255,
  };
}

export function rgbToHex(r: number, g: number, b: number): string {
  const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)));
  const toHex = (v: number) => clamp(v).toString(16).padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

// ─── UUID Big-Endian Helpers (16 Bytes) ──────────────────────────────

export function uuidToBytes(uuidStr: string): Uint8Array {
  const clean = uuidStr.replace(/-/g, '');
  const bytes = new Uint8Array(16);
  for (let i = 0; i < 16; i++) {
    bytes[i] = parseInt(clean.substring(i * 2, i * 2 + 2), 16) || 0;
  }
  return bytes;
}

export function bytesToUuid(bytes: Uint8Array, offset = 0): string {
  const hex: string[] = [];
  for (let i = 0; i < 16; i++) {
    hex.push(bytes[offset + i].toString(16).padStart(2, '0'));
  }
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-');
}

export function generateStrokeId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback RFC4122 v4 UUID generator
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ─── Encoders ────────────────────────────────────────────────────────

/**
 * Encode DRAW_START frame (28 bytes)
 * [version:1][opcode:1][round:2][strokeId:16][x:2][y:2][r:1][g:1][b:1][w:1]
 */
export function encodeDrawStart(data: DrawStartData): ArrayBuffer {
  const buffer = new ArrayBuffer(28);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);

  view.setUint8(0, data.version ?? PROTOCOL_VERSION);
  view.setUint8(1, DrawingOpcode.DRAW_START);
  view.setUint16(2, data.round, false); // big-endian

  bytes.set(uuidToBytes(data.strokeId), 4);

  view.setUint16(20, encodeCoordinate(data.x), false);
  view.setUint16(22, encodeCoordinate(data.y), false);

  const colorHex = data.tool === 'ERASER' ? '#FFFFFF' : data.colorHex;
  const { r, g, b } = hexToRgb(colorHex);
  view.setUint8(24, r);
  view.setUint8(25, g);
  view.setUint8(26, b);
  view.setUint8(27, Math.max(1, Math.min(64, Math.round(data.width))));

  return buffer;
}

/**
 * Encode DRAW_BATCH frame (26 + points.length * 4 bytes)
 * [version:1][opcode:1][round:2][seqStart:4][pointCount:2][strokeId:16][points:4*N]
 */
export function encodeDrawBatch(data: DrawBatchData): ArrayBuffer {
  const pointCount = Math.min(data.points.length, 256);
  const totalSize = 26 + pointCount * 4;
  const buffer = new ArrayBuffer(totalSize);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);

  view.setUint8(0, data.version ?? PROTOCOL_VERSION);
  view.setUint8(1, DrawingOpcode.DRAW_BATCH);
  view.setUint16(2, data.round, false);

  view.setUint32(4, data.seqStart, false);
  view.setUint16(8, pointCount, false);

  bytes.set(uuidToBytes(data.strokeId), 10);

  let offset = 26;
  for (let i = 0; i < pointCount; i++) {
    view.setUint16(offset, encodeCoordinate(data.points[i].x), false);
    view.setUint16(offset + 2, encodeCoordinate(data.points[i].y), false);
    offset += 4;
  }

  return buffer;
}

/**
 * Encode DRAW_END frame (20 bytes)
 * [version:1][opcode:1][round:2][strokeId:16]
 */
export function encodeDrawEnd(data: DrawEndData): ArrayBuffer {
  const buffer = new ArrayBuffer(20);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);

  view.setUint8(0, data.version ?? PROTOCOL_VERSION);
  view.setUint8(1, DrawingOpcode.DRAW_END);
  view.setUint16(2, data.round, false);

  bytes.set(uuidToBytes(data.strokeId), 4);

  return buffer;
}

/**
 * Encode CLEAR_CANVAS frame (4 bytes)
 * [version:1][opcode:1][round:2]
 */
export function encodeClearCanvas(data: ClearCanvasData): ArrayBuffer {
  const buffer = new ArrayBuffer(4);
  const view = new DataView(buffer);

  view.setUint8(0, data.version ?? PROTOCOL_VERSION);
  view.setUint8(1, DrawingOpcode.CLEAR_CANVAS);
  view.setUint16(2, data.round, false);

  return buffer;
}

// ─── Decoders ────────────────────────────────────────────────────────

export function decodeDrawingFrame(buffer: ArrayBuffer): DecodedDrawingMessage | null {
  if (buffer.byteLength < 4) return null;

  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);

  const version = view.getUint8(0);
  const opcode = view.getUint8(1);
  const round = view.getUint16(2, false);

  switch (opcode) {
    case DrawingOpcode.DRAW_START: {
      if (buffer.byteLength < 28) return null;
      const strokeId = bytesToUuid(bytes, 4);
      const x = decodeCoordinate(view.getUint16(20, false));
      const y = decodeCoordinate(view.getUint16(22, false));
      const r = view.getUint8(24);
      const g = view.getUint8(25);
      const b = view.getUint8(26);
      const width = view.getUint8(27);
      const colorHex = rgbToHex(r, g, b);
      const isEraser = r === 255 && g === 255 && b === 255;

      return {
        type: 'DRAW_START',
        data: {
          version,
          round,
          strokeId,
          x,
          y,
          colorHex,
          width,
          tool: isEraser ? 'ERASER' : 'BRUSH',
        },
      };
    }

    case DrawingOpcode.DRAW_BATCH: {
      if (buffer.byteLength < 26) return null;
      const seqStart = view.getUint32(4, false);
      const pointCount = view.getUint16(8, false);
      const strokeId = bytesToUuid(bytes, 10);

      const points: NormalizedPoint[] = [];
      let offset = 26;
      for (let i = 0; i < pointCount && offset + 4 <= buffer.byteLength; i++) {
        const px = decodeCoordinate(view.getUint16(offset, false));
        const py = decodeCoordinate(view.getUint16(offset + 2, false));
        points.push({ x: px, y: py });
        offset += 4;
      }

      return {
        type: 'DRAW_BATCH',
        data: {
          version,
          round,
          strokeId,
          seqStart,
          points,
        },
      };
    }

    case DrawingOpcode.DRAW_END: {
      if (buffer.byteLength < 20) return null;
      const strokeId = bytesToUuid(bytes, 4);
      return {
        type: 'DRAW_END',
        data: {
          version,
          round,
          strokeId,
        },
      };
    }

    case DrawingOpcode.CLEAR_CANVAS: {
      return {
        type: 'CLEAR_CANVAS',
        data: {
          version,
          round,
        },
      };
    }

    default:
      return null;
  }
}
