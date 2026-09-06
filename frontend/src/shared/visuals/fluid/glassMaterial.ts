/** Refraction of a procedural environment through the simulated density field.
 * DOM text stays above the canvas; it is never sampled or distorted.
 */
export const liquidGlassDisplay = `
  varying vec2 vUv;
  uniform sampler2D dye;
  uniform sampler2D velocity;
  uniform vec2 dyeTexel;
  uniform vec2 velocityTexel;
  uniform vec2 size;
  uniform float radius;
  uniform float dark;
  uniform float time;
  uniform float panel;
  uniform float seed;
  uniform vec3 tint;

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

  float density(vec2 p) {
    return dot(texture2D(dye, p).rgb, vec3(0.28, 0.40, 0.32));
  }

  void main() {
    float depth = density(vUv);
    vec2 sampleStep = dyeTexel * 2.0;
    vec2 gradient = vec2(
      density(vUv + vec2(sampleStep.x, 0.0)) - density(vUv - vec2(sampleStep.x, 0.0)),
      density(vUv + vec2(0.0, sampleStep.y)) - density(vUv - vec2(0.0, sampleStep.y))
    );
    vec3 normal = normalize(vec3(-gradient * 9.0, 1.0));
    vec2 flow = texture2D(velocity, vUv).xy * velocityTexel;
    vec2 p = (vUv - 0.5) * vec2(size.x / max(size.y, 1.0), 1.0);
    vec2 refracted = p + normal.xy * 0.28 + flow * 0.16;
    refracted += vec2(seed * 0.73, time * 0.025);

    // Broad transparent folds coexist with the fine, persistent simulation trails.
    float environment = noise(refracted * 3.4 + vec2(depth * 1.6, -depth));
    float fineLayer = noise(refracted * 7.0 - vec2(depth, depth * 0.6));
    float sheet = smoothstep(0.22, 0.80, environment * 0.72 + fineLayer * 0.28);
    float thickness = 1.0 - exp(-depth * 1.5);
    float fold = pow(clamp(length(gradient) * 5.0, 0.0, 1.0), 0.65);
    vec3 light = normalize(vec3(-0.45, 0.55, 1.0));
    float reflection = pow(max(dot(normal, light), 0.0), 18.0);
    float fresnel = pow(1.0 - normal.z, 2.0);
    float illumination = sheet * 0.16 + thickness * 0.09 + fold * 0.14 + reflection * 0.11;

    vec3 graphite = vec3(0.033, 0.045, 0.058);
    vec3 silver = vec3(0.64, 0.72, 0.77);
    // Tint follows the simulated folds and thickness; silver peaks stay reflective.
    float pigment = clamp(sheet * 0.36 + thickness * 0.28 + fold * 0.24, 0.0, 0.72);
    vec3 ice = mix(silver, tint, 0.78);
    vec3 darkGlass = graphite + mix(silver, tint, 0.65) * illumination + tint * pigment * 0.19 + ice * fresnel * 0.06;
    vec3 lightGlass = vec3(0.93, 0.95, 0.96) - silver * (sheet * 0.15 + thickness * 0.07)
      + vec3(0.06) * fold;
    lightGlass = mix(lightGlass, tint * 0.52 + vec3(0.45), pigment * 0.72);
    vec3 color = mix(lightGlass, darkGlass, dark);

    // A soft absorption layer keeps dense news copy readable without flattening the folds.
    float readingVeil = (1.0 - smoothstep(0.05, 0.85, vUv.x)) * panel * 0.14;
    color = mix(color, mix(vec3(0.93, 0.95, 0.96), graphite, dark), readingVeil);
    color += (hash(gl_FragCoord.xy) - 0.5) * 0.009;
    vec2 q = abs((vUv - 0.5) * size) - (size * 0.5 - radius);
    float edge = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float mask = 1.0 - smoothstep(-1.0, 0.0, edge);
    float alpha = mix(0.88, 0.62, panel) + thickness * 0.12 + fresnel * 0.06;
    gl_FragColor = vec4(color, min(alpha, 0.98) * mask);
  }
`;
