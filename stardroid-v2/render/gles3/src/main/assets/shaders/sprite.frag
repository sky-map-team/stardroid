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
const int MODE_RULE = 2;

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
    if (vMode == MODE_RULE) {
        // A solid bar: the underline beneath a label whose object has an info card, saying
        // that tapping it will actually show you something. A distinction that is otherwise
        // invisible until you tap and nothing happens.
        //
        // Deliberately a rule and not a dot. The first attempt drew a small filled disc beside
        // the text, which is the same shape, the same shader path and the same colour as a
        // star — so in a star field it read as one more star rather than as a mark on the
        // label. A horizontal bar under the text cannot be misread that way at any size.
        color = vTint;
    } else if (vMode == MODE_GLYPH) {
        // An R8 coverage mask, not colour: a quarter of the ARGB_8888 atlas GLES1 uploads.
        float fill = texture(uTexture, vUv).r;
        float halo = haloCoverage();
        // Outline under fill, both faded by the label's own alpha (which carries the declutter
        // fade and the has-an-info-card dimming).
        vec4 outline = vec4(uHaloColor.rgb, uHaloColor.a * halo);
        vec4 glyph = vec4(vTint.rgb, fill);
        // Straight-alpha "glyph over outline" (Porter-Duff over), not a plain mix(): mix()
        // interpolates rgb by glyph.a alone and ignores outline.a entirely, so at an
        // anti-aliased glyph edge (glyph.a partway between 0 and 1) it blends toward
        // uHaloColor even when the halo is fully off (uHaloTexels == 0, outline.a == 0) —
        // the near-black outline colour bleeds into every soft edge regardless of whether it
        // is actually visible. Compositing properly makes outline.a == 0 contribute nothing.
        color.a = outline.a + glyph.a * (1.0 - outline.a);
        color.rgb = color.a > 0.0
            ? (glyph.rgb * glyph.a + outline.rgb * outline.a * (1.0 - glyph.a)) / color.a
            : vec3(0.0);
        color.a *= vTint.a;
        if (color.a <= 0.0) discard;
    } else {
        vec4 texel = texture(uTexture, vUv);
        color = texel * vTint;
        if (color.a <= 0.0) discard;
    }
    fragColor = applyNightMode(color, uNightMode);
}
