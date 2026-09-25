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
