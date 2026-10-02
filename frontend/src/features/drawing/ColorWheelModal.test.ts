import { describe, expect, it } from 'vitest';
import { colorWheelPointToHex } from './ColorWheelModal';

describe('colorWheelPointToHex', () => {
  it('matches the CSS conic wheel cardinal colours', () => {
    expect(colorWheelPointToHex(0, -100, 100)).toBe('#ff0000');
    expect(colorWheelPointToHex(100, 0, 100)).toBe('#80ff00');
    expect(colorWheelPointToHex(0, 100, 100)).toBe('#00ffff');
    expect(colorWheelPointToHex(-100, 0, 100)).toBe('#8000ff');
  });

  it('fades saturation to white at the centre', () => {
    expect(colorWheelPointToHex(0, 0, 100)).toBe('#ffffff');
  });
});
