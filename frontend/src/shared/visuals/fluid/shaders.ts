// Fluid kernels adapted from Pavel Dobryakov's WebGL-Fluid-Simulation (MIT).
// Particle advection follows Volcomix/ink-drop (MIT). See THIRD_PARTY.md.
const field = `
  varying vec2 vUv;
  uniform sampler2D source;
  uniform vec2 texel;
  #define L (vUv - vec2(texel.x, 0.0))
  #define R (vUv + vec2(texel.x, 0.0))
  #define T (vUv + vec2(0.0, texel.y))
  #define B (vUv - vec2(0.0, texel.y))
`;

export const splat = field + `
  uniform vec2 point;
  uniform vec3 color;
  uniform float aspect;
  uniform float radius;
  void main() {
    vec2 p = vUv - point;
    p.x *= aspect;
    gl_FragColor = vec4(texture2D(source, vUv).xyz + exp(-dot(p,p) / radius) * color, 1.0);
  }
`;

export const advect = field + `
  uniform sampler2D velocity;
  uniform float dt;
  uniform float decay;
  void main() {
    vec2 p = vUv - dt * texture2D(velocity, vUv).xy * texel;
    gl_FragColor = texture2D(source, p) / (1.0 + dt * decay);
  }
`;

export const curl = field + `
  void main() {
    float c = texture2D(source, R).y - texture2D(source, L).y
      - texture2D(source, T).x + texture2D(source, B).x;
    gl_FragColor = vec4(c * 0.5, 0.0, 0.0, 1.0);
  }
`;

export const confine = field + `
  uniform sampler2D vorticity;
  uniform float dt;
  void main() {
    float c = texture2D(vorticity, vUv).r;
    vec2 force = 0.5 * vec2(abs(texture2D(vorticity, T).r) - abs(texture2D(vorticity, B).r),
      abs(texture2D(vorticity, R).r) - abs(texture2D(vorticity, L).r));
    force /= length(force) + 0.0001;
    force *= 12.0 * c;
    force.y *= -1.0;
    vec2 v = clamp(texture2D(source, vUv).xy + force * dt, -180.0, 180.0);
    gl_FragColor = vec4(v, 0.0, 1.0);
  }
`;

export const divergence = field + `
  void main() {
    vec2 c = texture2D(source, vUv).xy;
    float l = L.x < 0.0 ? -c.x : texture2D(source, L).x;
    float r = R.x > 1.0 ? -c.x : texture2D(source, R).x;
    float t = T.y > 1.0 ? -c.y : texture2D(source, T).y;
    float b = B.y < 0.0 ? -c.y : texture2D(source, B).y;
    gl_FragColor = vec4(0.5 * (r-l+t-b), 0.0, 0.0, 1.0);
  }
`;

export const pressure = field + `
  uniform sampler2D divergenceField;
  void main() {
    float p = (texture2D(source, L).r + texture2D(source, R).r
      + texture2D(source, T).r + texture2D(source, B).r - texture2D(divergenceField, vUv).r) * 0.25;
    gl_FragColor = vec4(p, 0.0, 0.0, 1.0);
  }
`;

export const project = field + `
  uniform sampler2D pressureField;
  void main() {
    vec2 v = texture2D(source, vUv).xy - vec2(
      texture2D(pressureField, R).r - texture2D(pressureField, L).r,
      texture2D(pressureField, T).r - texture2D(pressureField, B).r);
    gl_FragColor = vec4(v, 0.0, 1.0);
  }
`;

export const fade = field + `
  void main() { gl_FragColor = texture2D(source, vUv) * 0.75; }
`;

export const particleStep = `
  varying vec2 vUv;
  uniform sampler2D positions;
  uniform sampler2D velocity;
  uniform vec2 texel;
  uniform float dt;
  uniform float time;
  uniform float initialize;
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  void main() {
    vec4 state = texture2D(positions, vUv);
    float depth = hash(vUv * 7.13);
    if (initialize > 0.5) {
      state = vec4(hash(vUv), hash(vUv + 9.72), depth, 1.0);
    }
    vec2 v = texture2D(velocity, state.xy).xy * texel;
    vec2 drift = vec2(sin(time * 0.09 + depth * 12.0), cos(depth * 17.0 + time * 0.08)) * 0.006;
    state.xy = fract(state.xy + (v * 0.35 + drift) * dt + 1.0);
    // Travel toward the camera, wrapping at the near plane with a soft fade.
    state.z = fract(state.z - dt * 0.018 + 1.0);
    gl_FragColor = state;
  }
`;

export const particleVertex = `
  attribute vec2 reference;
  uniform sampler2D positions;
  uniform vec2 pointer;
  uniform vec2 size;
  uniform float pixelRatio;
  uniform float time;
  varying float depth;
  varying float visibility;
  varying float variety;
  void main() {
    vec4 state = texture2D(positions, reference);
    depth = state.z;
    variety = fract(sin(dot(reference, vec2(127.1, 311.7))) * 43758.5453);
    float aspect = size.x / max(size.y, 1.0);
    vec2 plane = (state.xy - 0.5) * vec2(aspect, 1.0) * 20.0;
    float angle = time * 0.012;
    plane = mat2(cos(angle), -sin(angle), sin(angle), cos(angle)) * plane;
    vec3 world = vec3(plane - pointer * vec2(aspect, 1.0) * 0.75, -2.0 - depth * 18.0);
    vec4 viewPosition = modelViewMatrix * vec4(world, 1.0);
    gl_Position = projectionMatrix * viewPosition;
    gl_PointSize = clamp(mix(2.8, 6.0, variety) * 9.0 / -viewPosition.z, 1.5, 10.0) * pixelRatio;
    visibility = smoothstep(0.0, 0.08, depth) * (1.0 - smoothstep(0.88, 1.0, depth));
  }
`;

export const particleFragment = `
  uniform float dark;
  uniform float time;
  varying float depth;
  varying float visibility;
  varying float variety;
  void main() {
    vec2 p = gl_PointCoord - 0.5;
    float d = length(p);
    float core = 1.0 - smoothstep(0.05, 0.24, d);
    float halo = exp(-d * d * 18.0) * 0.30;
    float rays = exp(-min(abs(p.x), abs(p.y)) * 65.0) * (1.0 - smoothstep(0.05, 0.48, d));
    float shimmer = 0.76 + 0.24 * sin(time * 1.1 + variety * 43.0);
    float alpha = (core + halo + rays * step(0.94, variety) * 0.28)
      * mix(0.48, 0.88, variety) * shimmer * visibility;
    vec3 cool = vec3(0.63, 0.78, 0.98);
    vec3 warm = vec3(0.95, 0.76, 0.79);
    vec3 color = mix(vec3(0.30, 0.43, 0.61), mix(cool, warm, variety), dark);
    gl_FragColor = vec4(color, alpha * mix(0.65, 1.0, dark));
  }
`;
