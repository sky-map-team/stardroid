// Instanced screen-space quads: icons, label glyph runs, and the info-card underline. One
// shader, three modes, as GLES3's sprite.vert/sprite.frag, which it transcribes. Instances are
// SpriteInstances (:render:api): positioned in bottom-left-origin pixels, as GL is.

struct SpriteInstance {
    packed_float2 centerPx;
    packed_float2 sizePx;
    packed_float2 uv0;
    packed_float2 uv1;
    packed_float4 tint;
    float mode;
};

struct SpriteUniforms {
    // xy: viewport size px. zw: one texel of the bound texture, which the halo taps step by.
    float4 viewport;
    // The label outline colour.
    float4 haloColor;
    // x: halo width in texels (0 for no halo). y: night mode (0 or 1).
    float4 params;
};

struct SpriteOut {
    float4 position [[position]];
    float2 uv;
    float4 tint;
    int mode [[flat]];
};

constant int MODE_ICON = 0;
constant int MODE_GLYPH = 1;
constant int MODE_RULE = 2;

vertex SpriteOut sprite_vertex(
    uint vid [[vertex_id]],
    uint iid [[instance_id]],
    const device SpriteInstance* instances [[buffer(0)]],
    constant SpriteUniforms& u [[buffer(1)]]
) {
    SpriteInstance s = instances[iid];
    float2 corner = unitQuadCorner(vid);
    float2 posPx = float2(s.centerPx) + (corner - float2(0.5)) * float2(s.sizePx);
    SpriteOut out;
    out.position = float4(posPx / u.viewport.xy * 2.0 - 1.0, 0.0, 1.0);
    out.uv = mix(float2(s.uv0), float2(s.uv1), corner);
    out.tint = float4(s.tint);
    out.mode = int(s.mode + 0.5);
    return out;
}

// Glyph pages are sampled nearest, so text stays crisp at 1:1; icons linearly. As GLES3.
constexpr sampler nearestClamp(filter::nearest, address::clamp_to_edge);
constexpr sampler linearClamp(filter::linear, address::clamp_to_edge);

// Peak coverage in a ring around uv: the widened glyph the halo is drawn from. Eight taps: the
// four axes plus the four diagonals, shortened to keep the ring roughly circular.
inline float haloCoverage(texture2d<float> page, float2 uv, float2 texel, float haloTexels) {
    if (haloTexels <= 0.0) return 0.0;
    float2 step = texel * haloTexels;
    const float diagonal = 0.7071;
    float peak = 0.0;
    peak = max(peak, page.sample(nearestClamp, uv + float2(step.x, 0.0)).r);
    peak = max(peak, page.sample(nearestClamp, uv - float2(step.x, 0.0)).r);
    peak = max(peak, page.sample(nearestClamp, uv + float2(0.0, step.y)).r);
    peak = max(peak, page.sample(nearestClamp, uv - float2(0.0, step.y)).r);
    peak = max(peak, page.sample(nearestClamp, uv + step * diagonal).r);
    peak = max(peak, page.sample(nearestClamp, uv - step * diagonal).r);
    peak = max(peak, page.sample(nearestClamp, uv + float2(step.x, -step.y) * diagonal).r);
    peak = max(peak, page.sample(nearestClamp, uv + float2(-step.x, step.y) * diagonal).r);
    return peak;
}

fragment float4 sprite_fragment(
    SpriteOut in [[stage_in]],
    texture2d<float> tex [[texture(0)]],
    constant SpriteUniforms& u [[buffer(1)]]
) {
    float4 color;
    if (in.mode == MODE_RULE) {
        // A solid bar: the underline beneath a label whose object has an info card.
        color = in.tint;
    } else if (in.mode == MODE_GLYPH) {
        // A single-channel coverage mask, not colour.
        float fill = tex.sample(nearestClamp, in.uv).r;
        float halo = haloCoverage(tex, in.uv, u.viewport.zw, u.params.x);
        float4 outline = float4(u.haloColor.rgb, u.haloColor.a * halo);
        float4 glyph = float4(in.tint.rgb, fill);
        // Straight-alpha "glyph over outline" (Porter-Duff over), so a halo that is off
        // contributes nothing at an antialiased edge; GLES3's sprite.frag explains why not mix().
        color.a = outline.a + glyph.a * (1.0 - outline.a);
        color.rgb = color.a > 0.0
            ? (glyph.rgb * glyph.a + outline.rgb * outline.a * (1.0 - glyph.a)) / color.a
            : float3(0.0);
        color.a *= in.tint.a;
        if (color.a <= 0.0) discard_fragment();
    } else {
        color = tex.sample(linearClamp, in.uv) * in.tint;
        if (color.a <= 0.0) discard_fragment();
    }
    return applyNightMode(color, u.params.y);
}
