export function localPointer(clientX: number, clientY: number, rect: Pick<DOMRect, 'left' | 'top' | 'width' | 'height'>) {
  return {
    x: Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width))),
    y: 1 - Math.max(0, Math.min(1, (clientY - rect.top) / Math.max(1, rect.height)))
  };
}

/** A session only steps down: repeated quality oscillation is distracting. */
export class FluidQuality {
  level: number;
  private slowFrames = 0;

  constructor(compact: boolean) {
    this.level = compact ? 1 : 2;
  }

  record(frameCost: number) {
    this.slowFrames = frameCost > 25 ? this.slowFrames + 1 : Math.max(0, this.slowFrames - 1);
    if (this.slowFrames >= 60 && this.level > 0) {
      this.level -= 1;
      this.slowFrames = 0;
    }
  }

  pixelRatio(deviceRatio: number) {
    return Math.min(deviceRatio || 1, [0.85, 1, 1.5][this.level]);
  }
}
