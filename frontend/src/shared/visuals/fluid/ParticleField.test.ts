import { describe, expect, it, vi } from 'vitest';
import { PerspectiveCamera, Vector2, Vector3 } from 'three';
import type { WebGLRenderer } from 'three';
import { ParticleField } from './ParticleField';
import type { GpuPass } from './gpu';

describe('ParticleField', () => {
  it('projects near stars larger than far stars and adapts to the viewport', () => {
    const field = new ParticleField({ draw: vi.fn() } as unknown as GpuPass);
    const render = vi.fn();
    const renderer = {
      getPixelRatio: () => 1,
      getSize: (target: Vector2) => target.set(1200, 600),
      render
    } as unknown as WebGLRenderer;
    field.render(renderer, new Vector2(), true, 2);
    const camera = render.mock.calls[0][1] as PerspectiveCamera;
    expect(camera.isPerspectiveCamera).toBe(true);
    expect(camera.aspect).toBe(2);
    const near = new Vector3(1, 0, -3).project(camera);
    const far = new Vector3(1, 0, -12).project(camera);
    expect(near.x).toBeGreaterThan(far.x * 3);
    field.dispose();
  });
});
