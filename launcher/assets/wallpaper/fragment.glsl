
precision mediump float;

varying vec2 vUv;

uniform float u_time;
uniform float u_ratio;
uniform vec2 u_pointer_position;
uniform float u_pointer_strength;
uniform float u_intensity;
uniform float u_detail;
uniform vec3 u_color_a;
uniform vec3 u_color_b;
uniform vec3 u_accent_color;
uniform float u_accent_strength;

vec2 rotate(vec2 uv, float th) {
    return mat2(cos(th), sin(th), -sin(th), cos(th)) * uv;
}

float neuro_shape(vec2 uv, float t, float pointerInfluence, float detailFactor) {
    vec2 sine_acc = vec2(0.0);
    vec2 res = vec2(0.0);
    float scale = mix(5.8, 10.8, detailFactor);

    for (int j = 0; j < 18; j++) {
        uv = rotate(uv, 0.825);
        sine_acc = rotate(sine_acc, 1.0);
        vec2 layer = uv * scale + float(j) + sine_acc - t;
        sine_acc += sin(layer) + 2.3 * pointerInfluence;
        res += (0.5 + 0.5 * cos(layer)) / scale;
        scale *= 1.16;
    }
    return res.x + res.y;
}

float dendrite_shape(vec2 uv, float t, float pointerInfluence, float detailFactor) {
    vec2 branch = uv;
    float strands = 0.0;
    float scale = mix(18.0, 30.0, detailFactor);

    for (int i = 0; i < 6; i++) {
        branch = rotate(branch + 0.04 * sin(branch.yx * 3.4 + t * 0.16), 0.74);
        vec2 layer = branch * scale + vec2(t * 0.42, -t * 0.28) + float(i) * 1.9;
        float filament = 1.0 - smoothstep(0.0, 0.024, abs(sin(layer.x + sin(layer.y) * 1.35)));
        float breaks = smoothstep(0.58, 0.985, 0.5 + 0.5 * cos(layer.y * 0.73 + float(i) * 1.6));
        float taper = 1.0 - smoothstep(0.72, 1.35, length(branch));
        strands += filament * breaks * taper / sqrt(scale);
        scale *= 1.28;
    }

    return strands * (0.06 + pointerInfluence * 0.035);
}

void main() {
    vec2 uv = 0.5 * vUv;
    uv.x *= u_ratio;
    uv += 0.032 * vec2(
        sin((vUv.y + u_time * 0.07) * 7.0),
        cos((vUv.x - u_time * 0.05) * 5.0)
    );

    vec2 pointer = vUv - u_pointer_position;
    pointer.x *= u_ratio;
    float pointerDistance = clamp(length(pointer), 0.0, 1.0);
    float pointerInfluence = 0.49 * pow(1.0 - pointerDistance, 2.04) * u_pointer_strength;

    float noise = neuro_shape(uv, u_time, pointerInfluence, u_detail);
    float dendrites = dendrite_shape(uv, u_time, pointerInfluence, u_detail);
    noise = 1.2 * pow(max(noise, 0.0), mix(2.5, 3.8, u_detail));
    noise += dendrites * smoothstep(0.24, 0.84, noise);
    noise += pow(noise, 10.0);
    noise = max(0.0, noise - 0.5);
    noise *= (1.0 - length(vUv - 0.5));

    float glow = clamp(noise, 0.0, 1.0);
    vec3 blendedColor = mix(u_color_a, u_color_b, smoothstep(0.15, 0.85, glow));
    float accentMask = smoothstep(0.46, 0.95, glow) * (0.55 + pointerInfluence);
    blendedColor = mix(blendedColor, u_accent_color, accentMask * u_accent_strength);
    vec3 finalColor = blendedColor * glow * u_intensity;

    gl_FragColor = vec4(finalColor, glow);
}
