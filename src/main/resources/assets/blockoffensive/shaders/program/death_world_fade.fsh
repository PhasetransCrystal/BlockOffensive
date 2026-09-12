#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float HitStrength;
uniform float VignetteStrength;
uniform float BlurStrength;
uniform float PresentationProgress;
uniform float FadeProgress;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec2 texel = 1.0 / max(OutSize, vec2(1.0));

    // A small world-only Gaussian-style kernel gives the death view a soft,
    // unstable focus without affecting the HUD, which is drawn later.
    vec3 color = texture(DiffuseSampler, texCoord).rgb * 0.28;
    color += texture(DiffuseSampler, texCoord + vec2(texel.x, 0.0)).rgb * 0.12;
    color += texture(DiffuseSampler, texCoord - vec2(texel.x, 0.0)).rgb * 0.12;
    color += texture(DiffuseSampler, texCoord + vec2(0.0, texel.y)).rgb * 0.12;
    color += texture(DiffuseSampler, texCoord - vec2(0.0, texel.y)).rgb * 0.12;
    color += texture(DiffuseSampler, texCoord + texel).rgb * 0.06;
    color += texture(DiffuseSampler, texCoord - texel).rgb * 0.06;
    color += texture(DiffuseSampler, texCoord + vec2(texel.x, -texel.y)).rgb * 0.06;
    color += texture(DiffuseSampler, texCoord + vec2(-texel.x, texel.y)).rgb * 0.06;

    // The red grade stays enabled for the complete presentation and grows
    // slightly over its two-second lifetime.
    float build = clamp(PresentationProgress, 0.0, 1.0);
    float redAmount = clamp(HitStrength + build * 0.22, 0.0, 1.0);
    vec3 redGrade = color * vec3(1.20, 0.38, 0.38);
    vec3 graded = mix(color, redGrade, redAmount);

    // Aspect-correct radial edge distance keeps the center relatively bright
    // while the perimeter becomes a pronounced dark red vignette.
    vec2 centered = texCoord - vec2(0.5);
    centered.x *= OutSize.x / max(1.0, OutSize.y);
    float edge = smoothstep(0.22, 0.78, length(centered) * 1.42);
    float vignette = clamp(edge * VignetteStrength * (0.62 + 0.28 * build), 0.0, 0.88);
    graded *= mix(vec3(1.0), vec3(0.48, 0.08, 0.08), vignette);

    // Blur ramps only through the world image; the center remains readable.
    vec3 sharp = texture(DiffuseSampler, texCoord).rgb;
    graded = mix(sharp, graded, clamp(BlurStrength * (0.45 + 0.55 * build), 0.0, 1.0));

    // The world-only fade starts after the fall and reaches pure black at the
    // end of the presentation. It does not cover the HUD drawn afterwards.
    float fade = clamp(FadeProgress, 0.0, 1.0);
    graded = mix(graded, vec3(0.0), fade);
    fragColor = vec4(clamp(graded, 0.0, 1.0), source.a);
}
