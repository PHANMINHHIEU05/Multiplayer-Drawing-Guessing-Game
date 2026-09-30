export interface PixelBuffer {
  data: Uint8ClampedArray;
  width: number;
  height: number;
}

export interface RgbColor {
  r: number;
  g: number;
  b: number;
}

function pixelMatches(
  data: Uint8ClampedArray,
  offset: number,
  target: [number, number, number, number],
): boolean {
  return (
    data[offset] === target[0] &&
    data[offset + 1] === target[1] &&
    data[offset + 2] === target[2] &&
    data[offset + 3] === target[3]
  );
}

function setPixel(
  data: Uint8ClampedArray,
  offset: number,
  color: RgbColor,
): void {
  data[offset] = color.r;
  data[offset + 1] = color.g;
  data[offset + 2] = color.b;
  data[offset + 3] = 255;
}

/**
 * Scanline flood fill. Mutates the supplied pixel buffer and returns whether
 * any pixel changed.
 */
export function floodFillPixels(
  image: PixelBuffer,
  startX: number,
  startY: number,
  color: RgbColor,
): boolean {
  const { data, width, height } = image;
  if (width <= 0 || height <= 0 || data.length < width * height * 4) {
    return false;
  }
  const x = Math.max(0, Math.min(width - 1, Math.floor(startX)));
  const y = Math.max(0, Math.min(height - 1, Math.floor(startY)));
  const startOffset = (y * width + x) * 4;
  const target: [number, number, number, number] = [
    data[startOffset],
    data[startOffset + 1],
    data[startOffset + 2],
    data[startOffset + 3],
  ];

  if (
    target[0] === color.r &&
    target[1] === color.g &&
    target[2] === color.b &&
    target[3] === 255
  ) {
    return false;
  }

  const stack: Array<[number, number]> = [[x, y]];
  let changed = false;

  while (stack.length > 0) {
    const [seedX, seedY] = stack.pop()!;
    let scanX = seedX;

    while (
      scanX >= 0 &&
      pixelMatches(data, (seedY * width + scanX) * 4, target)
    ) {
      scanX -= 1;
    }
    scanX += 1;

    let reachesAbove = false;
    let reachesBelow = false;
    for (
      ;
      scanX < width &&
      pixelMatches(data, (seedY * width + scanX) * 4, target);
      scanX += 1
    ) {
      setPixel(data, (seedY * width + scanX) * 4, color);
      changed = true;

      if (seedY > 0) {
        const matchesAbove = pixelMatches(
          data,
          ((seedY - 1) * width + scanX) * 4,
          target,
        );
        if (matchesAbove && !reachesAbove) {
          stack.push([scanX, seedY - 1]);
        }
        reachesAbove = matchesAbove;
      }

      if (seedY < height - 1) {
        const matchesBelow = pixelMatches(
          data,
          ((seedY + 1) * width + scanX) * 4,
          target,
        );
        if (matchesBelow && !reachesBelow) {
          stack.push([scanX, seedY + 1]);
        }
        reachesBelow = matchesBelow;
      }
    }
  }

  return changed;
}

export function hexToRgbColor(hex: string): RgbColor {
  const normalized = hex.trim().replace(/^#/, "");
  const expanded =
    normalized.length === 3
      ? normalized
          .split("")
          .map((digit) => digit + digit)
          .join("")
      : normalized;
  const value = Number.parseInt(expanded, 16);

  if (expanded.length !== 6 || Number.isNaN(value)) {
    return { r: 0, g: 0, b: 0 };
  }

  return {
    r: (value >> 16) & 255,
    g: (value >> 8) & 255,
    b: value & 255,
  };
}
