import { describe, it, expect } from 'vitest';
import {
  encodeCoordinate,
  decodeCoordinate,
  hexToRgb,
  rgbToHex,
  uuidToBytes,
  bytesToUuid,
  encodeDrawStart,
  encodeDrawBatch,
  encodeDrawEnd,
  encodeClearCanvas,
  decodeDrawingFrame,
  PROTOCOL_VERSION,
} from './binaryCodec';

describe('BinaryDrawingCodec', () => {
  describe('Coordinate Quantization', () => {
    it('encodes and decodes boundary coordinates', () => {
      expect(encodeCoordinate(0.0)).toBe(0);
      expect(encodeCoordinate(1.0)).toBe(65535);
      expect(decodeCoordinate(0)).toBe(0);
      expect(decodeCoordinate(65535)).toBe(1);
    });

    it('clamps out of bounds coordinates', () => {
      expect(encodeCoordinate(-0.5)).toBe(0);
      expect(encodeCoordinate(1.5)).toBe(65535);
    });

    it('round-trips arbitrary normalized coordinates with high precision', () => {
      const original = 0.4523;
      const encoded = encodeCoordinate(original);
      const decoded = decodeCoordinate(encoded);
      expect(Math.abs(decoded - original)).toBeLessThan(1 / 65535);
    });
  });

  describe('Color Helpers', () => {
    it('converts hex to RGB and back', () => {
      const hex = '#ef4444';
      const rgb = hexToRgb(hex);
      expect(rgb).toEqual({ r: 239, g: 68, b: 68 });
      expect(rgbToHex(rgb.r, rgb.g, rgb.b)).toBe(hex);
    });

    it('handles 3-digit short hex codes', () => {
      const rgb = hexToRgb('#fff');
      expect(rgb).toEqual({ r: 255, g: 255, b: 255 });
    });
  });

  describe('UUID Serialization', () => {
    it('round-trips UUID to bytes and back', () => {
      const uuid = '123e4567-e89b-12d3-a456-426614174000';
      const bytes = uuidToBytes(uuid);
      expect(bytes.length).toBe(16);
      const restored = bytesToUuid(bytes);
      expect(restored).toBe(uuid);
    });
  });

  describe('Frame Encoding and Decoding', () => {
    const strokeId = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d';

    it('encodes and decodes DRAW_START frame (28 bytes)', () => {
      const buffer = encodeDrawStart({
        round: 2,
        strokeId,
        x: 0.25,
        y: 0.75,
        colorHex: '#3b82f6',
        width: 8,
      });

      expect(buffer.byteLength).toBe(28);

      const decoded = decodeDrawingFrame(buffer);
      expect(decoded).not.toBeNull();
      if (!decoded || decoded.type !== 'DRAW_START') throw new Error('Expected DRAW_START');

      expect(decoded.data.version).toBe(PROTOCOL_VERSION);
      expect(decoded.data.round).toBe(2);
      expect(decoded.data.strokeId).toBe(strokeId);
      expect(Math.abs(decoded.data.x - 0.25)).toBeLessThan(0.001);
      expect(Math.abs(decoded.data.y - 0.75)).toBeLessThan(0.001);
      expect(decoded.data.colorHex.toLowerCase()).toBe('#3b82f6');
      expect(decoded.data.width).toBe(8);
    });

    it('encodes and decodes DRAW_BATCH frame (26 + 4*N bytes)', () => {
      const points = [
        { x: 0.1, y: 0.2 },
        { x: 0.3, y: 0.4 },
        { x: 0.5, y: 0.6 },
      ];

      const buffer = encodeDrawBatch({
        round: 1,
        strokeId,
        seqStart: 100,
        points,
      });

      expect(buffer.byteLength).toBe(26 + 3 * 4); // 38 bytes

      const decoded = decodeDrawingFrame(buffer);
      expect(decoded).not.toBeNull();
      if (!decoded || decoded.type !== 'DRAW_BATCH') throw new Error('Expected DRAW_BATCH');

      expect(decoded.data.round).toBe(1);
      expect(decoded.data.strokeId).toBe(strokeId);
      expect(decoded.data.seqStart).toBe(100);
      expect(decoded.data.points.length).toBe(3);
      expect(Math.abs(decoded.data.points[0].x - 0.1)).toBeLessThan(0.001);
      expect(Math.abs(decoded.data.points[1].y - 0.4)).toBeLessThan(0.001);
    });

    it('encodes and decodes DRAW_END frame (20 bytes)', () => {
      const buffer = encodeDrawEnd({
        round: 3,
        strokeId,
      });

      expect(buffer.byteLength).toBe(20);

      const decoded = decodeDrawingFrame(buffer);
      expect(decoded).not.toBeNull();
      if (!decoded || decoded.type !== 'DRAW_END') throw new Error('Expected DRAW_END');

      expect(decoded.data.round).toBe(3);
      expect(decoded.data.strokeId).toBe(strokeId);
    });

    it('encodes and decodes CLEAR_CANVAS frame (4 bytes)', () => {
      const buffer = encodeClearCanvas({ round: 5 });

      expect(buffer.byteLength).toBe(4);

      const decoded = decodeDrawingFrame(buffer);
      expect(decoded).not.toBeNull();
      if (!decoded || decoded.type !== 'CLEAR_CANVAS') throw new Error('Expected CLEAR_CANVAS');

      expect(decoded.data.round).toBe(5);
    });

    it('encodes and decodes DRAW_START frame with ERASER tool', () => {
      const buffer = encodeDrawStart({
        round: 1,
        strokeId,
        x: 0.5,
        y: 0.5,
        colorHex: '#000000',
        width: 16,
        tool: 'ERASER',
      });

      expect(buffer.byteLength).toBe(28);

      const decoded = decodeDrawingFrame(buffer);
      expect(decoded).not.toBeNull();
      if (!decoded || decoded.type !== 'DRAW_START') throw new Error('Expected DRAW_START');

      expect(decoded.data.tool).toBe('ERASER');
      expect(decoded.data.colorHex.toLowerCase()).toBe('#ffffff');
      expect(decoded.data.width).toBe(16);
    });

    it('returns null on truncated frames', () => {
      const shortBuffer = new ArrayBuffer(2);
      expect(decodeDrawingFrame(shortBuffer)).toBeNull();
    });
  });
});
