// Stars and fixed points: one buffer, one draw call per layer. Transcribed from GLES3's
// point.vert/point.frag. Layout matches PointVertices (:render:api).

struct PointVertex {
    packed_float3 pos;
    packed_float4 color;
    float sizeDp;
    float magnitude;
};

struct PointOut {
    float4 position [[position]];
    float pointSize [[point_size]];
    float4 color;
};

vertex PointOut point_vertex(
    uint vid [[vertex_id]],
    const device PointVertex* vertices [[buffer(0)]],
    constant FrameUniforms& u [[buffer(1)]]
) {
    PointVertex p = vertices[vid];
    float4 color = float4(p.color);
    PointOut out;
    out.position = toMetalClip(u.viewProj * float4(float3(p.pos), 1.0));
    out.pointSize = p.sizeDp * u.viewport.z;
    float alpha = color.a * magnitudeAlpha(p.magnitude, u.params.x);
    out.color = applyNightMode(float4(color.rgb, alpha), u.viewport.w);
    return out;
}

// An analytic anti-aliased disc: the same shape on every device because we draw it ourselves.
fragment float4 point_fragment(PointOut in [[stage_in]], float2 pointCoord [[point_coord]]) {
    // 0 at the centre of the point sprite, 1 at the inscribed circle's edge.
    float d = length(pointCoord - float2(0.5)) * 2.0;
    // One pixel of feathering, in the same units as d so it holds at any point size.
    float aa = max(fwidth(d), 1e-4);
    float coverage = 1.0 - smoothstep(1.0 - aa, 1.0, d);
    if (coverage <= 0.0) discard_fragment();
    return float4(in.color.rgb, in.color.a * coverage);
}
