// The horizon glow: concentric vertex rings filled into bands, blended additively so the glow
// adds light to whatever is behind it. Transcribed from GLES3's glow.vert/glow.frag; layout
// matches GlowMesh.

struct GlowVertex {
    packed_float3 pos;
    packed_float4 color;
};

struct GlowOut {
    float4 position [[position]];
    float4 color;
};

vertex GlowOut glow_vertex(
    uint vid [[vertex_id]],
    const device GlowVertex* vertices [[buffer(0)]],
    constant FrameUniforms& u [[buffer(1)]]
) {
    GlowVertex g = vertices[vid];
    GlowOut out;
    out.position = toMetalClip(u.viewProj * float4(float3(g.pos), 1.0));
    out.color = applyNightMode(float4(g.color), u.viewport.w);
    return out;
}

fragment float4 glow_fragment(GlowOut in [[stage_in]]) {
    return in.color;
}
