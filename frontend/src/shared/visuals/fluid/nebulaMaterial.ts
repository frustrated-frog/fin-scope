/** An opaque sky also prevents transparent star RGB from being alpha-multiplied twice by the browser. */
export const nebulaDisplay = `
  varying vec2 vUv;
  uniform float time;
  uniform float dark;
  uniform vec2 size;
  uniform vec2 pointer;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
  }
  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
      mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  float cloud(vec2 p) {
    return noise(p) * 0.57 + noise(p * 2.03 + 8.4) * 0.28 + noise(p * 4.1 - 3.2) * 0.15;
  }
  void main() {
    vec2 p = (vUv - 0.5) * vec2(size.x / max(size.y, 1.0), 1.0);
    p += pointer * 0.025;
    float t = time * 0.035;
    float wisps = cloud(p * 3.0 + vec2(t, -t * 0.5));
    float detail = cloud(p * 5.0 + vec2(wisps * 1.5, t * 0.7));
    float band = exp(-pow((p.y + p.x * 0.35 + (wisps - 0.5) * 0.45) * 2.8, 2.0));
    float mist = smoothstep(0.22, 0.82, wisps * 0.6 + detail * 0.4) * band;
    vec3 hue = mix(vec3(0.08, 0.46, 0.76), vec3(0.53, 0.20, 0.82), smoothstep(-0.7, 0.7, p.x));
    float roseCloud = smoothstep(0.25, 0.8, p.x + p.y * 0.5) * detail;
    hue = mix(hue, vec3(0.76, 0.24, 0.48), roseCloud * 0.65);
    vec3 night = vec3(0.047, 0.065, 0.115) + hue * (0.045 + mist * 0.55);
    vec3 day = vec3(0.85, 0.88, 0.94) + (hue - vec3(0.38)) * (0.025 + mist * 0.28);
    gl_FragColor = vec4(mix(day, night, dark), 1.0);
  }
`;
