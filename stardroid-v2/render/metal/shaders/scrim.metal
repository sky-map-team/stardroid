// The camera dimmer: a full-screen black quad at the given opacity, drawn before every layer
// while the background is transparent (D64). Transcribed from GLES3's scrim.vert/scrim.frag.

struct ScrimOut {
    float4 position [[position]];
};

vertex ScrimOut scrim_vertex(uint vid [[vertex_id]]) {
    float2 ndc = unitQuadCorner(vid) * 2.0 - 1.0;
    ScrimOut out;
    out.position = float4(ndc, 0.0, 1.0);
    return out;
}

fragment float4 scrim_fragment(ScrimOut in [[stage_in]], constant float4& opacity [[buffer(0)]]) {
    return float4(0.0, 0.0, 0.0, opacity.x);
}
