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

export const inkDisplay = `
  varying vec2 vUv;
  uniform sampler2D dye;
  uniform vec2 size;
  uniform float radius;
  uniform float dark;
  uniform vec3 tint;
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  void main() {
    vec3 ink = texture2D(dye, vUv).rgb;
    float density = length(ink);
    vec2 texel = 1.0 / size;
    float dx = length(texture2D(dye, vUv + vec2(texel.x, 0.0)).rgb) - density;
    float dy = length(texture2D(dye, vUv + vec2(0.0, texel.y)).rgb) - density;
    float relief = clamp((dx + dy) * 8.0, -0.08, 0.08);
    vec3 darkBase = vec3(0.022, 0.038, 0.048);
    vec3 color = mix(vec3(0.91, 0.95, 0.94) - ink * 0.26, darkBase + ink * 0.47, dark);
    color += tint * (0.025 + relief);
    color += (hash(gl_FragCoord.xy) - 0.5) * 0.027;
    // Soft central veil reserves contrast for the DOM labels and numbers.
    float veil = (1.0 - smoothstep(0.08, 0.70, vUv.x)) * 0.18;
    color = mix(color, mix(vec3(0.95), darkBase, dark), veil);
    vec2 q = abs((vUv - 0.5) * size) - (size * 0.5 - radius);
    float edge = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float mask = 1.0 - smoothstep(-1.0, 0.0, edge);
    gl_FragColor = vec4(max(color, vec3(0.0)), mask);
  }
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
    vec2 drift = vec2(sin(time * 0.09 + depth * 12.0), cos(depth * 17.0 + time * 0.08)) * 0.003;
    state.xy = fract(state.xy + (v * 0.35 + drift) * dt + 1.0);
    gl_FragColor = state;
  }
`;

export const particleVertex = `
  attribute vec2 reference;
  uniform sampler2D positions;
  uniform vec2 pointer;
  uniform vec2 size;
  uniform float pixelRatio;
  varying float depth;
  void main() {
    vec4 state = texture2D(positions, reference);
    depth = state.z;
    vec2 xy = state.xy * 2.0 - 1.0 + pointer * (depth - 0.5) * 0.022;
    gl_Position = vec4(xy, 0.0, 1.0);
    gl_PointSize = mix(1.5, 4.4, depth * depth) * pixelRatio;
  }
`;

export const particleFragment = `
  uniform float dark;
  varying float depth;
  void main() {
    float d = length(gl_PointCoord - 0.5);
    float alpha = (1.0 - smoothstep(0.10, 0.5, d)) * mix(0.20, 0.56, depth);
    vec3 color = mix(vec3(0.14, 0.40, 0.35), vec3(0.43, 0.79, 0.74), dark);
    color = mix(color, mix(vec3(0.43, 0.34, 0.22), vec3(0.85, 0.68, 0.44), dark), step(0.88, depth));
    gl_FragColor = vec4(color, alpha);
  }
`;
