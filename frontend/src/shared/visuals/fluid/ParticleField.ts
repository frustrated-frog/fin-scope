import { BufferGeometry, Float32BufferAttribute, OrthographicCamera, Points, Scene, ShaderMaterial, Vector2 } from 'three';
import type { Texture, WebGLRenderer } from 'three';
import { GpuPass, SwapTarget } from './gpu';
import { particleFragment, particleStep, particleVertex } from './shaders';

const PARTICLE_SIDE = 32;

export class ParticleField {
  private state = new SwapTarget(PARTICLE_SIDE, PARTICLE_SIDE, true);
  private scene = new Scene();
  private camera = new OrthographicCamera(-1, 1, 1, -1, 0, 1);
  private geometry = new BufferGeometry();
  private uniforms = {
    positions: { value: this.state.read.texture }, pointer: { value: new Vector2() },
    size: { value: new Vector2() }, dark: { value: 0 }, pixelRatio: { value: 1 }
  };
  private material = new ShaderMaterial({
    vertexShader: particleVertex, fragmentShader: particleFragment, uniforms: this.uniforms,
    transparent: true, depthWrite: false, depthTest: false
  });
  private texel = new Vector2();
  private initialized = false;

  constructor(private gpu: GpuPass) {
    const references = new Float32Array(PARTICLE_SIDE * PARTICLE_SIDE * 2);
    for (let index = 0; index < PARTICLE_SIDE * PARTICLE_SIDE; index += 1) {
      references[index * 2] = (index % PARTICLE_SIDE + 0.5) / PARTICLE_SIDE;
      references[index * 2 + 1] = (Math.floor(index / PARTICLE_SIDE) + 0.5) / PARTICLE_SIDE;
    }
    this.geometry.setAttribute('position', new Float32BufferAttribute(new Float32Array(PARTICLE_SIDE * PARTICLE_SIDE * 3), 3));
    this.geometry.setAttribute('reference', new Float32BufferAttribute(references, 2));
    const points = new Points(this.geometry, this.material);
    points.frustumCulled = false;
    this.scene.add(points);
  }

  step(velocity: Texture, width: number, height: number, dt: number, time: number) {
    this.texel.set(1 / width, 1 / height);
    this.gpu.draw(particleStep, {
      positions: this.state.read.texture, velocity, texel: this.texel, dt, time, initialize: this.initialized ? 0 : 1
    }, this.state.write);
    this.state.swap();
    this.initialized = true;
  }

  render(renderer: WebGLRenderer, pointer: Vector2, dark: boolean, level: number) {
    this.uniforms.positions.value = this.state.read.texture;
    this.uniforms.pointer.value.copy(pointer);
    this.uniforms.dark.value = dark ? 1 : 0;
    this.uniforms.pixelRatio.value = renderer.getPixelRatio();
    this.geometry.setDrawRange(0, [384, 640, 1024][level]);
    renderer.render(this.scene, this.camera);
  }

  dispose() {
    this.state.dispose();
    this.geometry.dispose();
    this.material.dispose();
    this.scene.clear();
  }
}
