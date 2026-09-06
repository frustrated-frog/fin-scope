import { NoToneMapping, Vector2, Vector3, WebGLRenderer } from 'three';
import { FluidSimulation } from './FluidSimulation';
import { GpuPass } from './gpu';
import { ParticleField } from './ParticleField';
import { FluidQuality, localPointer, simulationDimensions } from './quality';
import { liquidGlassDisplay } from './glassMaterial';

export type FlowMode = 'ambient' | 'cards' | 'panels';
export interface FlowController {
  setActive(active: boolean): void;
  setMotion(motion: boolean): void;
  refresh(): void;
  destroy(): void;
}
export type FlowFactory = (canvas: HTMLCanvasElement, host: HTMLElement, options: {
  mode: FlowMode;
  motion: boolean;
  onFailure: () => void;
}) => FlowController;

const FLOW_DYES = [new Vector3(0.62, 0.72, 0.77), new Vector3(0.16, 0.26, 0.32)];

export const createFlowRenderer: FlowFactory = (canvas, host, options) => {
  const context = canvas.getContext('webgl2', { alpha: true, antialias: false, premultipliedAlpha: false });
  if (!context || !context.getExtension('EXT_color_buffer_float')) {
    throw new Error('Floating point render targets unavailable');
  }
  // Float particle coordinates require nearest sampling; request filtering only for half-float fluid fields.
  const renderer = new WebGLRenderer({ canvas, context, alpha: true, antialias: false, premultipliedAlpha: false });
  renderer.autoClear = false;
  renderer.toneMapping = NoToneMapping;
  renderer.setClearColor(0x000000, 0);
  renderer.debug.onShaderError = () => { throw new Error('Fluid shader compilation failed'); };
  const gpu = new GpuPass(renderer);
  const surfaces = options.mode === 'ambient' ? [] : Array.from(host.querySelectorAll<HTMLElement>('[data-flow-surface]'));
  const interactionHost = options.mode === 'ambient' ? host.closest<HTMLElement>('.app-shell') ?? host : host;
  const quality = new FluidQuality(window.innerWidth < 760);
  const simulations: FluidSimulation[] = [];
  const surfaceTints = surfaces.map(() => new Vector3(0.45, 0.63, 0.68));
  const particles = options.mode === 'ambient' ? new ParticleField(gpu) : undefined;
  const pointer = new Vector2();
  const targetPointer = new Vector2();
  const size = new Vector2();
  const dyeTexel = new Vector2();
  const velocityTexel = new Vector2();
  const dyeColor = new Vector3();
  const previous = { x: 0, y: 0, card: -2 };
  let pending: { x: number; y: number; card: number } | undefined;
  let frame = 0;
  let active = true;
  let motion = options.motion;
  let destroyed = false;
  let dirty = true;
  let lastTime = 0;
  let elapsed = 0;
  let lastFeed = -10;
  let interactiveUntil = 0;
  let width = 0;
  let height = 0;
  let dark = false;
  let currentLevel = -1;

  const seed = (simulation: FluidSimulation, index: number) => {
    const palette = FLOW_DYES;
    for (let drop = 0; drop < 9; drop += 1) {
      const angle = drop * 2.399 + index * 0.7;
      const x = 0.5 + Math.cos(angle) * 0.34;
      const y = 0.5 + Math.sin(angle) * 0.34;
      simulation.splat(x, y, Math.sin(angle) * 32, -Math.cos(angle) * 32, palette[drop % 2], 0.027);
    }
    // A composed still frame for reduced motion, not an empty canvas.
    for (let step = 0; step < 6; step += 1) {
      simulation.step(1 / 60, 10);
    }
  };

  const resize = () => {
    width = Math.max(1, host.clientWidth);
    height = Math.max(1, host.clientHeight);
    renderer.setPixelRatio(quality.pixelRatio(window.devicePixelRatio));
    renderer.setSize(width, height, false);
    // Keep the canvas at the scroll viewport origin; card rects already include scrolling.
    canvas.style.transform = `translate(${host.scrollLeft}px, ${host.scrollTop}px)`;
    dark = host.closest('[data-theme]')?.getAttribute('data-theme') === 'dark';
    surfaces.forEach((surface, index) => {
      const channels = getComputedStyle(surface).getPropertyValue('--flow-tint').trim().split(/\s+/).map(Number);
      if (channels.length === 3 && channels.every(Number.isFinite)) {
        surfaceTints[index].set(channels[0] / 255, channels[1] / 255, channels[2] / 255);
      }
    });
    const count = options.mode === 'ambient' ? 1 : surfaces.length;
    const grids = Array.from({ length: count }, (_, index) => {
      const rect = surfaces[index]?.getBoundingClientRect();
      return simulationDimensions(rect?.width ?? width, rect?.height ?? height, quality.level);
    });
    const geometryChanged = grids.some((grid, index) => {
      const previousGrid = simulations[index]?.velocity.read;
      return previousGrid?.width !== grid.width || previousGrid?.height !== grid.height;
    });
    if (currentLevel !== quality.level || geometryChanged) {
      simulations.forEach(simulation => simulation.dispose());
      simulations.length = 0;
      for (let index = 0; index < count; index += 1) {
        const grid = grids[index];
        const simulation = new FluidSimulation(gpu, grid.width, grid.height, options.mode === 'ambient' ? 1 : 3);
        simulations.push(simulation);
        seed(simulation, index);
      }
      currentLevel = quality.level;
    }
    host.dataset.flowQuality = String(quality.level);
    dirty = false;
  };

  const queueFrame = () => {
    if (!frame && !destroyed && active) {
      frame = requestAnimationFrame(draw);
    }
  };

  const onPointer = (event: PointerEvent) => {
    if (!motion || !active || event.pointerType === 'touch') {
      return;
    }
    const card = options.mode === 'ambient' ? -1 : surfaces.findIndex(element => element.contains(event.target as Node));
    if (card === -1 && options.mode !== 'ambient') {
      previous.card = -2;
      return;
    }
    pending = { x: event.clientX, y: event.clientY, card };
    interactiveUntil = performance.now() + 900;
    queueFrame();
  };

  const onLeave = () => {
    pending = undefined;
    previous.card = -2;
    targetPointer.set(0, 0);
  };

  const applyPointer = () => {
    if (!pending) {
      return;
    }
    const index = pending.card < 0 ? 0 : pending.card;
    const rect = (surfaces[index] ?? host).getBoundingClientRect();
    const point = localPointer(pending.x, pending.y, rect);
    const same = pending.card === previous.card;
    const dx = same ? Math.max(-0.15, Math.min(0.15, point.x - previous.x)) : 0;
    const dy = same ? Math.max(-0.15, Math.min(0.15, point.y - previous.y)) : 0;
    const palette = FLOW_DYES;
    dyeColor.copy(palette[0]).multiplyScalar(0.28);
    simulations[index]?.splat(point.x, point.y, dx * 850, dy * 850, dyeColor, 0.004);
    targetPointer.set(point.x * 2 - 1, point.y * 2 - 1);
    previous.x = point.x;
    previous.y = point.y;
    previous.card = pending.card;
    pending = undefined;
  };

  function draw(now: number) {
    frame = 0;
    if (destroyed || !active) {
      return;
    }
    const interval = 1000 / (quality.level === 0 ? 20 : now < interactiveUntil ? 60 : 30);
    if (!dirty && motion && now - lastTime < interval - 1) {
      queueFrame();
      return;
    }
    const begin = performance.now();
    const previousTime = lastTime;
    const dt = Math.min(0.05, lastTime ? (now - lastTime) / 1000 : 1 / 60);
    lastTime = now;
    try {
      renderer.setScissorTest(false);
      if (dirty) {
        resize();
      }
      const hostRect = host.getBoundingClientRect();
      const rects = surfaces.map(card => card.getBoundingClientRect());
      const visible = (index: number) => {
        const rect = rects[index];
        return !rect || (rect.bottom > 0 && rect.top < window.innerHeight && rect.right > hostRect.left && rect.left < hostRect.right);
      };
      if (motion) {
        elapsed += dt;
        applyPointer();
        pointer.lerp(targetPointer, 1 - Math.exp(-dt * 5));
        const feed = elapsed - lastFeed > 0.12;
        simulations.forEach((simulation, index) => {
          if (!visible(index)) {
            return;
          }
          if (feed) {
            const angle = elapsed * 0.32 + index * 2.1;
            dyeColor.copy(FLOW_DYES[Math.floor(elapsed / 8) % 2]).multiplyScalar(options.mode === 'ambient' ? 0.015 : 0.08);
            simulation.splat(0.5 + Math.cos(angle) * 0.31, 0.5 + Math.sin(angle * 1.3) * 0.3,
              Math.sin(angle * 1.4) * 4, Math.cos(angle) * 4, dyeColor, 0.009);
          }
          simulation.step(dt, [6, 9, 12][quality.level]);
        });
        if (feed) {
          lastFeed = elapsed;
        }
      }
      const ambient = simulations[0];
      if (particles && ambient) {
        particles.step(ambient.velocity.read.texture, ambient.velocity.read.width, ambient.velocity.read.height, motion ? dt : 0, elapsed);
      }
      renderer.setRenderTarget(null);
      renderer.setViewport(0, 0, width, height);
      renderer.clear();
      if (particles) {
        particles.render(renderer, pointer, dark, quality.level);
      } else {
        renderer.setScissorTest(true);
        surfaces.forEach((card, index) => {
          if (!visible(index)) {
            return;
          }
          const rect = rects[index];
          const x = rect.left - hostRect.left;
          const y = height - (rect.bottom - hostRect.top);
          renderer.setViewport(x, y, rect.width, rect.height);
          renderer.setScissor(Math.max(0, x), Math.max(0, y), Math.min(rect.width, width - Math.max(0, x)), Math.min(rect.height, height - Math.max(0, y)));
          size.set(rect.width, rect.height);
          const simulation = simulations[index];
          dyeTexel.set(1 / simulation.dye.read.width, 1 / simulation.dye.read.height);
          velocityTexel.set(1 / simulation.velocity.read.width, 1 / simulation.velocity.read.height);
          gpu.draw(liquidGlassDisplay, {
            dye: simulation.dye.read.texture, velocity: simulation.velocity.read.texture,
            dyeTexel, velocityTexel, size, dark: dark ? 1 : 0, time: elapsed,
            panel: options.mode === 'panels' ? 1 : 0, seed: index, tint: surfaceTints[index],
            radius: parseFloat(getComputedStyle(card).borderTopLeftRadius) || 12
          }, null);
        });
        renderer.setScissorTest(false);
      }
      // Include scheduling delay to notice GPU-bound frames, not only JS submission time.
      const cost = Math.max(performance.now() - begin, previousTime ? (now - previousTime) * 16.7 / interval : 0);
      quality.record(cost);
      if (quality.level !== currentLevel) {
        dirty = true;
      }
    } catch {
      options.onFailure();
      return;
    }
    if (motion) {
      queueFrame();
    }
  }

  const controller: FlowController = {
    setActive(next) {
      active = next;
      lastTime = 0;
      if (!next) {
        cancelAnimationFrame(frame);
        frame = 0;
        onLeave();
      } else {
        queueFrame();
      }
    },
    setMotion(next) {
      motion = next;
      onLeave();
      dirty = true;
      queueFrame();
    },
    refresh() {
      dirty = true;
      queueFrame();
    },
    destroy() {
      if (destroyed) {
        return;
      }
      destroyed = true;
      cancelAnimationFrame(frame);
      interactionHost.removeEventListener('pointermove', onPointer);
      interactionHost.removeEventListener('pointerleave', onLeave);
      canvas.removeEventListener('webglcontextlost', onContextLost);
      simulations.forEach(simulation => simulation.dispose());
      particles?.dispose();
      gpu.dispose();
      renderer.dispose();
      if (!context.isContextLost()) {
        renderer.forceContextLoss();
      }
    }
  };
  const onContextLost = (event: Event) => {
    event.preventDefault();
    options.onFailure();
  };
  interactionHost.addEventListener('pointermove', onPointer, { passive: true });
  interactionHost.addEventListener('pointerleave', onLeave);
  canvas.addEventListener('webglcontextlost', onContextLost);
  queueFrame();
  return controller;
};
