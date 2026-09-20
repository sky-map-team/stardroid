precision mediump float;

in vec2 vUv;
in vec4 vTint;
in vec2 vLocal;
flat in int vMode;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uNightMode;
// Outline colour and width in texels. A halo is what finally makes a label legible against
// whatever it happens to sit on — issue #1014 was a green horizon label on a green horizon glow,
// and the stopgap was to repaint the label. On GLES1 an outline means rasterising and drawing a
// second, wider glyph per label, against a batching TODO that already exists. Here it is a few
// extra taps of a coverage mask we are already sampling, in the same draw call.
uniform vec4 uHaloColor;
uniform float uHaloTexels;

out vec4 fragColor;

const int MODE_ICON = 0;
const int MODE_GLYPH = 1;
const int MODE_MARKER = 2;

/** Peak coverage in a ring around vUv — the widened glyph the halo is drawn from. */
float haloCoverage() {
    if (uHaloTexels <= 0.0) return 0.0;
    vec2 step = uTexelSize * uHaloTexels;
    float peak = 0.0;
    // Eight taps: the four axes plus the four diagonals, so corners are covered as evenly as
    // edges. The diagonals are shortened to keep the ring roughly circular.
    const float diagonal = 0.7071;
    peak = max(peak, texture(uTexture, vUv + vec2(step.x, 0.0)).r);
    peak = max(peak, texture(uTexture, vUv - vec2(step.x, 0.0)).r);
    peak = max(peak, texture(uTexture, vUv + vec2(0.0, step.y)).r);
    peak = max(peak, texture(uTexture, vUv - vec2(0.0, step.y)).r);
    peak = max(peak, texture(uTexture, vUv + step * diagonal).r);
    peak = max(peak, texture(uTexture, vUv - step * diagonal).r);
    peak = max(peak, texture(uTexture, vUv + vec2(step.x, -step.y) * diagonal).r);
    peak = max(peak, texture(uTexture, vUv + vec2(-step.x, step.y) * diagonal).r);
    return peak;
}

void main() {
    vec4 color;
    if (vMode == MODE_MARKER) {
        // A procedural filled dot: no texture, so it needs no atlas space and cannot collide
        // with a glyph cell. It says "this one has an info card, tapping it will show you
        // something" — a distinction that is currently invisible until you tap and nothing
        // happens.
        float d = length(vLocal - vec2(0.5)) * 2.0;
        float aa = max(fwidth(d), 1e-4);
        float coverage = 1.0 - smoothstep(1.0 - aa, 1.0, d);
        if (coverage <= 0.0) discard;
        color = vec4(vTint.rgb, vTint.a * coverage);
    } else if (vMode == MODE_GLYPH) {
        // An R8 coverage mask, not colour: a quarter of the ARGB_8888 atlas GLES1 uploads.
        float fill = texture(uTexture, vUv).r;
        float halo = haloCoverage();
        // Outline under fill, both faded by the label's own alpha (which carries the declutter
        // fade and the has-an-info-card dimming).
        vec4 outline = vec4(uHaloColor.rgb, uHaloColor.a * halo);
        vec4 glyph = vec4(vTint.rgb, fill);
        color.rgb = mix(outline.rgb, glyph.rgb, glyph.a);
        color.a = max(outline.a, glyph.a) * vTint.a;
        if (color.a <= 0.0) discard;
    } else {
        vec4 texel = texture(uTexture, vUv);
        color = texel * vTint;
        if (color.a <= 0.0) discard;
    }
    fragColor = applyNightMode(color, uNightMode);
}
