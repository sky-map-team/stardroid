// Spliced into every program after the #version line (GLSL ES has no #include).
// Shared helpers only: anything that belongs to one program lives in that program's source.

// Night mode (D12). GLES1 bakes this into every vertex colour on the CPU and keeps a second
// red-shifted copy of every texture; here it is one uniform and one function, which is what lets
// this backend hold a single texture per image.
vec4 applyNightMode(vec4 color, float nightMode) {
    if (nightMode < 0.5) return color;
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    return vec4(luminance, 0.0, 0.0, color.a);
}

// StellarRamps.magnitudeAlpha, transcribed. The Kotlin is the golden reference; the conformance
// test renders this through the shader and asserts they agree. Kotlin's `null` limit (no extra
// filtering) arrives here as a very large uMagnitudeLimit, which no real magnitude reaches.
float magnitudeAlpha(float magnitude, float magnitudeLimit) {
    const float fadeRange = 0.5;
    const float faintAlphaFloor = 0.15;
    float overshoot = magnitude - magnitudeLimit;
    if (overshoot <= 0.0) return 1.0;
    if (overshoot >= fadeRange) return 0.0;
    float t = 1.0 - overshoot / fadeRange;
    return faintAlphaFloor + (1.0 - faintAlphaFloor) * t;
}

// The four corners of a unit quad in triangle-strip order (LL, UL, LR, UR), from gl_VertexID.
// Quads are expanded in the vertex shader, so instanced geometry needs no vertex buffer at all.
vec2 unitQuadCorner(int vertexId) {
    return vec2(float(vertexId / 2), float(vertexId % 2));
}

// PhaseGeometry.litOffset, transcribed: how lit the point (x, y) on a unit disc is, in disc radii
// from the terminator — positive lit, negative in shadow, zero on the terminator. The lit limb
// faces +x; callers rotate into that frame.
//
// GLES1 has no way to do this per pixel, so PhaseCompositor evaluates it on the CPU for every
// texel of the Moon and caches the result against a *quantised* phase, which is why the
// terminator there steps between buckets instead of gliding.
float litOffset(float x, float y, float fraction) {
    float s = 2.0 * clamp(fraction, 0.0, 1.0) - 1.0;
    float yy = clamp(y, -1.0, 1.0);
    return x + s * sqrt(max(0.0, 1.0 - yy * yy));
}

// EclipseGeometry.tint, transcribed: a per-channel multiplier at point p on the unit disc, for a
// shadow centred at `center` with the given umbra and penumbra radii (all in Moon radii).
//
// The umbra darkens *and* reddens, because the light reaching it has been Rayleigh-scattered
// through Earth's atmosphere — the same reason sunsets are red. The penumbra only dims: that
// light is ordinary sunlight with Earth blocking part of the Sun's disc, and nothing filters it.
vec3 eclipseTint(vec2 p, float umbra, float penumbra, vec2 center) {
    const vec3 umbraEdge = vec3(0.55, 0.22, 0.16);
    const vec3 umbraCore = vec3(0.30, 0.06, 0.04);
    const float penumbraMaxDimming = 0.35;
    float dist = distance(p, center);
    if (dist <= umbra) {
        float depth = umbra > 0.0 ? clamp(1.0 - dist / umbra, 0.0, 1.0) : 1.0;
        return mix(umbraEdge, umbraCore, depth);
    }
    if (dist < penumbra) {
        float span = penumbra - umbra;
        float depth = span > 0.0 ? clamp((penumbra - dist) / span, 0.0, 1.0) : 1.0;
        return vec3(1.0 - penumbraMaxDimming * depth);
    }
    return vec3(1.0);
}

// GroundRamp, transcribed. The Kotlin is the golden reference; the conformance test renders these
// through the shader and asserts they agree.
//
// The split between the two inputs is the design. Opacity is a function of view altitude alone --
// a mostly uniform wash that celestial objects show through, denser near the horizon to suggest
// depth. Colour is a function of solar altitude alone -- lighter by day so the lower hemisphere
// does not read as a hole in a lit scene, darker by night. Nothing depends on azimuth, and nothing
// reads anything the sky shader computed: the ground is the same substance all the way round, and
// must not turn warm in the west at sunset.
//
// GLES1 evaluates these on the CPU, once per ring of a shell mesh whose ring altitudes trace the
// depth curve piecewise, which is the whole difference between the two backends here.
const float GROUND_EDGE_RAMP_DEG = 0.25;
const float GROUND_DEPTH_SCALE_DEG = 12.0;
const float GROUND_NADIR_FRACTION = 0.4;
const float GROUND_NIGHT_SUN_ALTITUDE_DEG = -18.0;
const float GROUND_DAY_SUN_ALTITUDE_DEG = 0.0;

// Half-width of the horizon edge, in degrees, sized so the transition is a fixed number of
// *pixels* rather than a fixed angle.
//
// Fragment stage only: fwidth is a screen-derivative builtin and does not exist in a vertex
// shader. This file is spliced into every program, so leaving it unguarded failed to compile
// every vertex shader in the app -- a crash on launch, found on device.
//
// A fixed angular ramp is invisible at a wide field of view and turns into a fat band when you
// zoom: at a couple of degrees across the screen, a quarter-degree ramp is sixty pixels of sky
// dissolving into ground, starting at the horizon line and finishing well below it, so the line
// no longer sits on the boundary. Reported from the device exactly that way. fwidth gives degrees
// per pixel, so scaling by it holds the edge at roughly one pixel however far in you go. The min
// is a safety cap, not a working value -- it only binds at absurdly wide fields.
#ifdef FRAGMENT_STAGE
float groundEdgeRampDeg(float viewAltitudeDeg) {
    return min(fwidth(viewAltitudeDeg), GROUND_EDGE_RAMP_DEG);
}
#endif

// Zero above the horizon, one below it, with an antialiasing ramp of half-width `rampDeg`
// straddling altitude zero -- so the horizon line, which is drawn at exactly zero, covers the
// blend rather than sitting at the top of it.
float groundCoverage(float viewAltitudeDeg, float rampDeg) {
    return 1.0 - smoothstep(-rampDeg, rampDeg, viewAltitudeDeg);
}

// The depth cue: 1 at the horizon, decaying to GROUND_NADIR_FRACTION below it.
float groundDepthProfile(float viewAltitudeDeg) {
    return GROUND_NADIR_FRACTION
        + (1.0 - GROUND_NADIR_FRACTION)
            * exp(-abs(viewAltitudeDeg) / GROUND_DEPTH_SCALE_DEG);
}

// The only thing the Sun controls, and it controls only the colour.
float groundDaylight(float sunAltitudeDeg) {
    return smoothstep(
        GROUND_NIGHT_SUN_ALTITUDE_DEG, GROUND_DAY_SUN_ALTITUDE_DEG, sunAltitudeDeg
    );
}

float groundAlpha(float viewAltitudeDeg, float opacity, float rampDeg) {
    return opacity * groundCoverage(viewAltitudeDeg, rampDeg) * groundDepthProfile(viewAltitudeDeg);
}
