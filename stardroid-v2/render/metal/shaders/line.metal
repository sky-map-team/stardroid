// Lines as mitered triangle strips (D117). Metal has no line width, and render-gles3.md §3.1
// rules out independent per-segment quads: our lines are translucent, so quads would double-blend
// at every joint. Instead each polyline vertex arrives twice (side -1 and +1) carrying its
// neighbours, and is pushed out along the miter of the two screen-space directions, so adjacent
// segments share their joint vertices and blend exactly once. Layout matches LineStrips.
//
// GLES3 strokes with aliased glLineWidth; here the edge is feathered over one pixel instead,
// which is the one intentional visual difference.

struct LineVertex {
    packed_float3 prev;
    packed_float3 cur;
    packed_float3 next;
    float side;
    packed_float4 color;
    float halfWidthDp;
};

struct LineOut {
    float4 position [[position]];
    float4 color;
    // Signed distance from the line's centre, in pixels, interpolated across the strip.
    float edgePx;
    float halfWidthPx;
};

// Clip-space w below which a vertex counts as at or behind the eye.
constant float NEAR_W = 1e-3;
// The miter may grow to this many half-widths at a sharp joint before it is clamped.
constant float MAX_MITER = 4.0;

// Moves `behind` along the segment toward `front` to where it crosses the eye plane, so a segment
// running behind the viewer still has a finite on-screen direction.
inline float4 pullToEyePlane(float4 front, float4 behind) {
    float t = (front.w - NEAR_W) / (front.w - behind.w);
    return mix(front, behind, t);
}

inline float2 toPixels(float4 clip, float2 halfViewport) {
    return clip.xy / clip.w * halfViewport;
}

vertex LineOut line_vertex(
    uint vid [[vertex_id]],
    const device LineVertex* vertices [[buffer(0)]],
    constant FrameUniforms& u [[buffer(1)]]
) {
    LineVertex v = vertices[vid];
    float4 prev = u.viewProj * float4(float3(v.prev), 1.0);
    float4 cur = u.viewProj * float4(float3(v.cur), 1.0);
    float4 next = u.viewProj * float4(float3(v.next), 1.0);

    LineOut out;
    out.color = applyNightMode(float4(v.color), u.viewport.w);
    out.halfWidthPx = v.halfWidthDp * u.viewport.z;
    out.edgePx = 0.0;

    if (cur.w < NEAR_W) {
        if (next.w >= NEAR_W) {
            cur = pullToEyePlane(next, cur);
        } else if (prev.w >= NEAR_W) {
            cur = pullToEyePlane(prev, cur);
        } else {
            // Both neighbours are behind too: every triangle using this vertex is behind the
            // viewer, and the rasterizer discards it whole.
            out.position = toMetalClip(cur);
            return out;
        }
    }
    if (prev.w < NEAR_W) prev = pullToEyePlane(cur, prev);
    if (next.w < NEAR_W) next = pullToEyePlane(cur, next);

    float2 halfViewport = u.viewport.xy * 0.5;
    float2 curPx = toPixels(cur, halfViewport);
    float2 inDir = curPx - toPixels(prev, halfViewport);
    float2 outDir = toPixels(next, halfViewport) - curPx;
    bool hasIn = dot(inDir, inDir) > 1e-6;
    bool hasOut = dot(outDir, outDir) > 1e-6;
    float2 tangentIn = hasIn ? normalize(inDir) : float2(0.0);
    float2 tangentOut = hasOut ? normalize(outDir) : float2(0.0);

    // The segment's own direction (either side of the joint will do for its normal), and the
    // miter direction bisecting the joint.
    float2 segment = hasIn ? tangentIn : (hasOut ? tangentOut : float2(1.0, 0.0));
    float2 tangent = segment;
    if (hasIn && hasOut && dot(tangentIn + tangentOut, tangentIn + tangentOut) > 1e-6) {
        tangent = normalize(tangentIn + tangentOut);
    }
    float2 normal = float2(-tangent.y, tangent.x);
    float2 segmentNormal = float2(-segment.y, segment.x);
    float miter = min(1.0 / max(dot(normal, segmentNormal), 1e-3), MAX_MITER);

    // Half a pixel of feather beyond the stroke on each side.
    float extent = out.halfWidthPx + 0.5;
    float2 offsetPx = normal * extent * miter * v.side;
    cur.xy += offsetPx / halfViewport * cur.w;

    out.position = toMetalClip(cur);
    out.edgePx = v.side * extent;
    return out;
}

fragment float4 line_fragment(LineOut in [[stage_in]]) {
    // Coverage of the stroke at this distance from its centre, feathered over one pixel.
    float coverage = clamp(in.halfWidthPx + 0.5 - abs(in.edgePx), 0.0, 1.0);
    if (coverage <= 0.0) discard_fragment();
    return float4(in.color.rgb, in.color.a * coverage);
}
