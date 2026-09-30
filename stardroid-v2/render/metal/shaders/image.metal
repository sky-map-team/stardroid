// World-anchored textured quads: planet discs and deep-sky photographs, with the Moon's phase
// and eclipse evaluated per pixel. Transcribed from GLES3's skyquad.vert/skyquad.frag. The
// quad's geometry comes from ImageQuad (:render:api), recomputed each frame for the FOV.

struct ImageUniforms {
    float4 center;       // xyz: the image's centre on the unit sphere
    float4 halfU;        // xyz: half-axis across, already rotated and scaled to the drawn size
    float4 halfV;        // xyz: half-axis up
    float4 terminator;   // x: has a terminator (0/1), y: illuminated fraction, zw: lit direction
    float4 eclipse;      // x: in Earth's shadow (0/1), y: umbra radius, z: penumbra radius
    float4 shadowCenter; // xy: the shadow axis in the disc frame, in Moon radii
};

struct ImageOut {
    float4 position [[position]];
    float2 uv;
    float2 disc;
};

vertex ImageOut image_vertex(
    uint vid [[vertex_id]],
    constant FrameUniforms& u [[buffer(1)]],
    constant ImageUniforms& img [[buffer(2)]]
) {
    float2 corner = unitQuadCorner(vid);
    // -1..1 across the quad in each axis.
    float2 signedCorner = corner * 2.0 - 1.0;
    float3 world = img.center.xyz + img.halfU.xyz * signedCorner.x + img.halfV.xyz * signedCorner.y;
    ImageOut out;
    out.position = toMetalClip(u.viewProj * float4(world, 1.0));
    // Texture v runs down the image while the quad's +v axis runs up it, as in GLES3.
    out.uv = float2(corner.x, 1.0 - corner.y);
    // The unit-disc frame the phase and eclipse geometry work in.
    out.disc = signedCorner;
    return out;
}

// MoonShading (:render:api), transcribed; MetalShaderConstantParityTest compares them.
constant float DARK_FLOOR = 0.10;
constant float EARTHSHINE = 0.13;
constant float LIMB_RING = 0.22;
constant float LIMB_RING_WIDTH = 0.04;
constant float TERMINATOR_SOFTNESS = 0.012;

fragment float4 image_fragment(
    ImageOut in [[stage_in]],
    texture2d<float> image [[texture(0)]],
    constant FrameUniforms& u [[buffer(1)]],
    constant ImageUniforms& img [[buffer(2)]]
) {
    // Linear filtering, clamped at the edges: GLES3's setLinearClampParams.
    constexpr sampler linearClamp(filter::linear, address::clamp_to_edge);
    float4 texel = image.sample(linearClamp, in.uv);
    if (texel.a <= 0.0) discard_fragment();
    float3 rgb = texel.rgb;

    if (img.terminator.x > 0.5) {
        float fraction = img.terminator.y;
        float2 litDirection = img.terminator.zw;
        // Rotate into the frame where the lit limb faces +u.
        float uLit = dot(in.disc, litDirection);
        float vLit = in.disc.x * litDirection.y - in.disc.y * litDirection.x;
        float offset = litOffset(uLit, vLit, fraction);
        float lit = smoothstep(-1.0, 1.0, offset / TERMINATOR_SOFTNESS);

        float darkness = 1.0 - clamp(fraction, 0.0, 1.0);
        float scale = DARK_FLOOR + (1.0 - DARK_FLOOR) * lit;
        scale += EARTHSHINE * darkness * (1.0 - lit);

        float radius = length(in.disc);
        if (radius > 1.0 - LIMB_RING_WIDTH) {
            scale += LIMB_RING * (radius - (1.0 - LIMB_RING_WIDTH)) / LIMB_RING_WIDTH;
        }
        rgb *= scale;
    }

    if (img.eclipse.x > 0.5) {
        rgb *= eclipseTint(in.disc, img.eclipse.y, img.eclipse.z, img.shadowCenter.xy);
    }

    return applyNightMode(float4(rgb, texel.a), u.viewport.w);
}
