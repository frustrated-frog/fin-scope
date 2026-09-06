import { Vector2, Vector3 } from 'three';
import { GpuPass, renderTarget, SwapTarget } from './gpu';
import * as shader from './shaders';

export class FluidSimulation {
  readonly velocity: SwapTarget;
  readonly dye: SwapTarget;
  private pressure: SwapTarget;
  private curl;
  private divergence;
  private texel: Vector2;
  private aspect: number;
  private point = new Vector2();
  private force = new Vector3();

  constructor(private gpu: GpuPass, width: number, height: number, dyeScale = 2) {
    this.velocity = new SwapTarget(width, height);
    this.dye = new SwapTarget(width * dyeScale, height * dyeScale);
    this.pressure = new SwapTarget(width, height);
    this.curl = renderTarget(width, height);
    this.divergence = renderTarget(width, height);
    this.texel = new Vector2(1 / width, 1 / height);
    this.aspect = width / height;
  }

  splat(x: number, y: number, dx: number, dy: number, color: Vector3, radius = 0.007) {
    this.point.set(x, y);
    this.force.set(dx, dy, 0);
    const values = { point: this.point, radius, aspect: this.aspect };
    this.gpu.draw(shader.splat, { ...values, source: this.velocity.read.texture, color: this.force }, this.velocity.write);
    this.velocity.swap();
    this.gpu.draw(shader.splat, { ...values, source: this.dye.read.texture, color }, this.dye.write);
    this.dye.swap();
  }

  step(dt: number, iterations: number) {
    this.gpu.draw(shader.curl, { source: this.velocity.read.texture, texel: this.texel }, this.curl);
    this.gpu.draw(shader.confine, {
      source: this.velocity.read.texture, vorticity: this.curl.texture, texel: this.texel, dt
    }, this.velocity.write);
    this.velocity.swap();
    this.gpu.draw(shader.divergence, { source: this.velocity.read.texture, texel: this.texel }, this.divergence);
    this.gpu.draw(shader.fade, { source: this.pressure.read.texture }, this.pressure.write);
    this.pressure.swap();
    for (let index = 0; index < iterations; index += 1) {
      this.gpu.draw(shader.pressure, {
        source: this.pressure.read.texture, divergenceField: this.divergence.texture, texel: this.texel
      }, this.pressure.write);
      this.pressure.swap();
    }
    this.gpu.draw(shader.project, {
      source: this.velocity.read.texture, pressureField: this.pressure.read.texture, texel: this.texel
    }, this.velocity.write);
    this.velocity.swap();
    this.gpu.draw(shader.advect, {
      source: this.velocity.read.texture, velocity: this.velocity.read.texture, texel: this.texel, dt, decay: 0.45
    }, this.velocity.write);
    this.velocity.swap();
    this.gpu.draw(shader.advect, {
      source: this.dye.read.texture, velocity: this.velocity.read.texture, texel: this.texel, dt, decay: 0.16
    }, this.dye.write);
    this.dye.swap();
  }

  dispose() {
    this.velocity.dispose();
    this.dye.dispose();
    this.pressure.dispose();
    this.curl.dispose();
    this.divergence.dispose();
  }
}
