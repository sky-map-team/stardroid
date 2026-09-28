// Shared by every program. The embed task concatenates this file first and the others after it
// into one library source, compiled at runtime — as :render:gles3 compiles its GLSL (D117).
//
// Transcribed from :render:gles3's common.glsl; the Kotlin in :render:api is the golden reference
// for the functions that have one (StellarRamps.magnitudeAlpha).

#include <metal_stdlib>
using namespace metal;

// Per-frame uniforms for the sky-anchored programs. Every member is float4-aligned, so Kotlin
// writes this as a flat float array (MetalUniforms) with no padding rules to get wrong.
struct FrameUniforms {
    // perspective × view, column-major, exactly Matrix4.toFloatArray(). GL clip convention.
    float4x4 viewProj;
    // x: viewport width px, y: viewport height px, z: density (dp → px), w: night mode (0 or 1).
    float4 viewport;
    // x: magnitude limit (a huge value when there is none). y, z, w: unused.
    float4 params;
};

// :render:api builds GL-convention matrices, whose clip volume is -w <= z <= w; Metal's is
// 0 <= z <= w. Remapping z this way makes the two volumes identical, so Metal clips exactly the
// geometry GL clips rather than roughly the same geometry.
inline float4 toMetalClip(float4 glClip) {
    return float4(glClip.xy, (glClip.z + glClip.w) * 0.5, glClip.w);
}

// Night mode (D12): one luminance-to-red transform for every colour, as in GLES3.
inline float4 applyNightMode(float4 color, float nightMode) {
    if (nightMode < 0.5) return color;
    float luminance = dot(color.rgb, float3(0.299, 0.587, 0.114));
    return float4(luminance, 0.0, 0.0, color.a);
}

// StellarRamps.magnitudeAlpha, transcribed.
inline float magnitudeAlpha(float magnitude, float magnitudeLimit) {
    const float fadeRange = 0.5;
    const float faintAlphaFloor = 0.15;
    float overshoot = magnitude - magnitudeLimit;
    if (overshoot <= 0.0) return 1.0;
    if (overshoot >= fadeRange) return 0.0;
    float t = 1.0 - overshoot / fadeRange;
    return faintAlphaFloor + (1.0 - faintAlphaFloor) * t;
}

// The four corners of a unit quad in triangle-strip order (LL, UL, LR, UR), from the vertex id.
inline float2 unitQuadCorner(uint vertexId) {
    return float2(float(vertexId / 2), float(vertexId % 2));
}

// MSL has no degrees()/radians().
constant float RADIANS_TO_DEGREES = 57.29577951308232;

// PhaseGeometry.litOffset, transcribed: how lit the point (x, y) on a unit disc is, in disc radii
// from the terminator — positive lit, negative in shadow. The lit limb faces +x.
inline float litOffset(float x, float y, float fraction) {
    float s = 2.0 * clamp(fraction, 0.0, 1.0) - 1.0;
    float yy = clamp(y, -1.0, 1.0);
    return x + s * sqrt(max(0.0, 1.0 - yy * yy));
}

// EclipseGeometry.tint, transcribed: a per-channel multiplier at point p on the unit disc for a
// shadow centred at `center` with the given umbra and penumbra radii, all in Moon radii. The
// umbra darkens and reddens (Rayleigh-scattered light); the penumbra only dims.
inline float3 eclipseTint(float2 p, float umbra, float penumbra, float2 center) {
    const float3 umbraEdge = float3(0.55, 0.22, 0.16);
    const float3 umbraCore = float3(0.30, 0.06, 0.04);
    const float penumbraMaxDimming = 0.35;
    float dist = distance(p, center);
    if (dist <= umbra) {
        float depth = umbra > 0.0 ? clamp(1.0 - dist / umbra, 0.0, 1.0) : 1.0;
        return mix(umbraEdge, umbraCore, depth);
    }
    if (dist < penumbra) {
        float span = penumbra - umbra;
        float depth = span > 0.0 ? clamp((penumbra - dist) / span, 0.0, 1.0) : 1.0;
        return float3(1.0 - penumbraMaxDimming * depth);
    }
    return float3(1.0);
}
