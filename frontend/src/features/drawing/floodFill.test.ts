import { describe, expect, it } from 'vitest';
import { floodFillPixels, hexToRgbColor } from './floodFill';

function whiteImage(width: number, height: number) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < data.length; i += 4) {
    data[i] = 255;
    data[i + 1] = 255;
    data[i + 2] = 255;
    data[i + 3] = 255;
  }
  return { data, width, height };
}

describe('floodFillPixels', () => {
  it('fills only the connected region', () => {
    const image = whiteImage(3, 3);
    for (let y = 0; y < 3; y += 1) {
      const offset = (y * 3 + 1) * 4;
      image.data[offset] = 0;
      image.data[offset + 1] = 0;
      image.data[offset + 2] = 0;
    }

    expect(floodFillPixels(image, 0, 1, hexToRgbColor('#ef4444'))).toBe(true);

    expect(Array.from(image.data.slice(12, 16))).toEqual([239, 68, 68, 255]);
    expect(Array.from(image.data.slice(16, 20))).toEqual([0, 0, 0, 255]);
    expect(Array.from(image.data.slice(20, 24))).toEqual([255, 255, 255, 255]);
  });

  it('treats transparent pixels left by the eraser as blank canvas', () => {
    const image = whiteImage(3, 1);
    image.data.set([0, 0, 0, 0], 4);

    expect(floodFillPixels(image, 0, 0, hexToRgbColor('#22c55e'))).toBe(true);
    expect(Array.from(image.data.slice(0, 4))).toEqual([34, 197, 94, 255]);
    expect(Array.from(image.data.slice(4, 8))).toEqual([34, 197, 94, 255]);
    expect(Array.from(image.data.slice(8, 12))).toEqual([34, 197, 94, 255]);
  });

  it('fills the surrounding blank canvas when clicking an erased pixel', () => {
    const image = whiteImage(3, 1);
    image.data.set([0, 0, 0, 0], 4);

    expect(floodFillPixels(image, 1, 0, hexToRgbColor('#f59e0b'))).toBe(true);
    expect(Array.from(image.data)).toEqual([
      245, 158, 11, 255,
      245, 158, 11, 255,
      245, 158, 11, 255,
    ]);
  });
});
