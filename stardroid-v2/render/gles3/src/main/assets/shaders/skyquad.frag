precision mediump float;

in vec2 vUv;
in vec2 vDisc;

uniform sampler2D uTexture;
uniform float uNightMode;
// Phase. uHasTerminator is 0 for anything not catching sunlight, and for the outer planets,
// which never drop below full illumination seen from Earth.
uniform float uHasTerminator;
uniform float uIlluminatedFraction;
// The lit limb's direction in texture space: (-sin, cos) of the position angle east of north.
uniform vec2 uLitDirection;
// Earth's shadow, when the Moon is in it: (umbraRadius, penumbraRadius) and the shadow centre,
// all in Moon radii. uHasEclipse is 0 on the ~360 nights a year when there is no eclipse.
uniform float uHasEclipse;
uniform vec2 uShadowRadii;
uniform vec2 uShadowCenter;

out vec4 fragColor;

// PhaseCompositor's constants, transcribed. They are shared with the GLES1 CPU compositor, which
// is the reference: this is the same function evaluated per pixel per frame instead of painted
// into a bitmap and cached against a quantised phase. The visible difference is that the
// terminator glides here rather than stepping between phase buckets.
const float DARK_FLOOR = 0.10;
const float EARTHSHINE = 0.13;
const float LIMB_RING = 0.22;
const float LIMB_RING_WIDTH = 0.04;
const float TERMINATOR_SOFTNESS = 0.012;

void main() {
    vec4 texel = texture(uTexture, vUv);
    if (texel.a <= 0.0) discard;
    vec3 rgb = texel.rgb;

    if (uHasTerminator > 0.5) {
        // Rotate into the frame where the lit limb faces +u.
        float u = dot(vDisc, uLitDirection);
        float v = vDisc.x * uLitDirection.y - vDisc.y * uLitDirection.x;
        float offset = litOffset(u, v, uIlluminatedFraction);
        // Soft, not a hard cut: a hard terminator is the tell of a fake.
        float lit = smoothstep(-1.0, 1.0, offset / TERMINATOR_SOFTNESS);

        // A New Moon painted black is invisible against a black sky and reads as the Moon having
        // vanished, so the shadowed side keeps a floor, plus earthshine — real sunlight off the
        // Earth, brightest exactly when it is needed most.
        float darkness = 1.0 - clamp(uIlluminatedFraction, 0.0, 1.0);
        float scale = DARK_FLOOR + (1.0 - DARK_FLOOR) * lit;
        scale += EARTHSHINE * darkness * (1.0 - lit);

        // A thin ring around the whole limb at every phase, so the disc's extent stays
        // locatable even when almost all of it is in shadow.
        float radius = length(vDisc);
        if (radius > 1.0 - LIMB_RING_WIDTH) {
            scale += LIMB_RING * (radius - (1.0 - LIMB_RING_WIDTH)) / LIMB_RING_WIDTH;
        }
        rgb *= scale;
    }

    if (uHasEclipse > 0.5) {
        rgb *= eclipseTint(vDisc, uShadowRadii.x, uShadowRadii.y, uShadowCenter);
    }

    fragColor = applyNightMode(vec4(rgb, texel.a), uNightMode);
}
