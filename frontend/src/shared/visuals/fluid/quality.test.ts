import { expect, test } from 'vitest';
import { FluidQuality, localPointer } from './quality';

test('maps card input into bounded bottom-origin coordinates', () => {
  expect(localPointer(150, 75, { left: 100, top: 50, width: 100, height: 100 })).toEqual({ x: 0.5, y: 0.75 });
  expect(localPointer(-20, 400, { left: 0, top: 0, width: 100, height: 100 })).toEqual({ x: 0, y: 0 });
});

test('reduces quality after sustained expensive frames, not a single hitch', () => {
  const quality = new FluidQuality(false);
  quality.record(80);
  expect(quality.level).toBe(2);
  for (let index = 0; index < 180; index += 1) {
    quality.record(35);
  }
  expect(quality.level).toBe(0);
  expect(quality.pixelRatio(3)).toBeLessThanOrEqual(1);
});

test('starts conservatively on small screens and bounds high density displays', () => {
  expect(new FluidQuality(true).level).toBe(1);
  expect(new FluidQuality(false).pixelRatio(3)).toBeLessThanOrEqual(1.5);
});
