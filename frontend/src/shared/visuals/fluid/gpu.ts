import {
  FloatType, HalfFloatType, LinearFilter, Mesh, NearestFilter, NoBlending, OrthographicCamera, PlaneGeometry,
  RGBAFormat, Scene, ShaderMaterial, WebGLRenderer, WebGLRenderTarget
} from 'three';
import type { IUniform } from 'three';

export const quadVertex = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`;

export function renderTarget(width: number, height: number, precise = false) {
  return new WebGLRenderTarget(width, height, {
    type: precise ? FloatType : HalfFloatType, format: RGBAFormat,
    minFilter: precise ? NearestFilter : LinearFilter, magFilter: precise ? NearestFilter : LinearFilter,
    depthBuffer: false, stencilBuffer: false, generateMipmaps: false
  });
}

export class SwapTarget {
  read: WebGLRenderTarget;
  write: WebGLRenderTarget;

  constructor(width: number, height: number, precise = false) {
    this.read = renderTarget(width, height, precise);
    this.write = renderTarget(width, height, precise);
  }

  swap() {
    [this.read, this.write] = [this.write, this.read];
  }

  dispose() {
    this.read.dispose();
    this.write.dispose();
  }
}

/** One fullscreen mesh and a cached material per kernel, shared by all simulations. */
export class GpuPass {
  private scene = new Scene();
  private camera = new OrthographicCamera(-1, 1, 1, -1, 0, 1);
  private geometry = new PlaneGeometry(2, 2);
  private placeholder = new ShaderMaterial();
  private mesh = new Mesh(this.geometry, this.placeholder);
  private materials = new Map<string, ShaderMaterial>();

  constructor(private renderer: WebGLRenderer) {
    this.scene.add(this.mesh);
  }

  draw(shader: string, values: Record<string, unknown>, target: WebGLRenderTarget | null) {
    let material = this.materials.get(shader);
    if (!material) {
      const uniforms: Record<string, IUniform> = {};
      for (const [key, value] of Object.entries(values)) {
        uniforms[key] = { value };
      }
      material = new ShaderMaterial({
        vertexShader: quadVertex, fragmentShader: shader, uniforms,
        depthTest: false, depthWrite: false, blending: NoBlending
      });
      this.materials.set(shader, material);
    }
    for (const [key, value] of Object.entries(values)) {
      material.uniforms[key].value = value;
    }
    this.mesh.material = material;
    this.renderer.setRenderTarget(target);
    this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    this.geometry.dispose();
    this.placeholder.dispose();
    this.materials.forEach(material => material.dispose());
    this.materials.clear();
    this.scene.clear();
  }
}
