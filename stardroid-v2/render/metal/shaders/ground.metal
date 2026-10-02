// The translucent ground below the horizon, as a full-screen quad: a continuous function of view
// direction, so there is nothing to tessellate. Transcribed from GLES3's ground.vert/ground.frag;
// the ramp lives in common.metal, transcribed from GroundRamp (:render:api).

struct GroundUniforms {
    float4 zenithDir;  // xyz
    float4 nightColor; // rgb
    float4 dayColor;   // rgb
    // x: the Sun's altitude in degrees (frame-constant, so resolved on the CPU). y: opacity.
    float4 params;
};

struct GroundOut {
    float4 position [[position]];
    float2 ndc;
};

vertex GroundOut ground_vertex(uint vid [[vertex_id]]) {
    GroundOut out;
    out.ndc = unitQuadCorner(vid) * 2.0 - 1.0;
    out.position = float4(out.ndc, 0.0, 1.0);
    return out;
}

fragment float4 ground_fragment(
    GroundOut in [[stage_in]],
    constant ViewRay& ray [[buffer(0)]],
    constant GroundUniforms& u [[buffer(1)]]
) {
    float opacity = u.params.y;
    if (opacity <= 0.0) discard_fragment();

    float3 dir = viewDirection(ray, in.ndc);
    float viewAltitudeDeg = asin(clamp(dot(dir, u.zenithDir.xyz), -1.0, 1.0)) * RADIANS_TO_DEGREES;

    // Everything clear of the horizon's edge ramp is sky; discarding keeps blending out of most
    // of a typical frame. The ramp straddles zero, so the test has to clear it.
    float rampDeg = groundEdgeRampDeg(viewAltitudeDeg);
    if (viewAltitudeDeg > rampDeg) discard_fragment();

    float3 color = mix(u.nightColor.rgb, u.dayColor.rgb, groundDaylight(u.params.x));
    return float4(color, groundAlpha(viewAltitudeDeg, opacity, rampDeg));
}
