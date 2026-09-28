// The sky dome as a full-screen quad, evaluated per pixel: Preetham daylight plus twilight.
// Transcribed line for line from GLES3's sky.vert/sky.frag, which carries the reasoning behind
// every term; comments here only mark where MSL differs.

struct SkyUniforms {
    // Camera basis in celestial coordinates (xyz; w unused), mirroring Matrix4.view.
    float4 camRight;
    float4 camUp;
    float4 camForward;
    float4 sunDir;
    float4 zenithDir;
    // x, y: tangent of the half-FOV along each screen axis. z: turbidity. w: dither seed.
    float4 params;
};

struct SkyOut {
    float4 position [[position]];
    float2 ndc;
};

vertex SkyOut sky_vertex(uint vid [[vertex_id]]) {
    SkyOut out;
    out.ndc = unitQuadCorner(vid) * 2.0 - 1.0;
    out.position = float4(out.ndc, 0.0, 1.0);
    return out;
}

constant float PI = 3.14159265359;

struct PerezCoeffs {
    float a;
    float b;
    float c;
    float d;
    float e;
};

inline float perez(float cosTheta, float gamma, float cosGamma, PerezCoeffs c) {
    return (1.0 + c.a * exp(c.b / max(cosTheta, 0.01)))
        * (1.0 + c.c * exp(c.d * gamma) + c.e * cosGamma * cosGamma);
}

inline float zenithLuminance(float turbidity, float thetaSun) {
    float chi = (4.0 / 9.0 - turbidity / 120.0) * (PI - 2.0 * thetaSun);
    return (4.0453 * turbidity - 4.9710) * tan(chi) - 0.2155 * turbidity + 2.4192;
}

inline float2 zenithChromaticity(float turbidity, float thetaSun) {
    float t = turbidity;
    float t2 = t * t;
    float s = thetaSun;
    float s2 = s * s;
    float s3 = s2 * s;

    float x = (0.00166 * s3 - 0.00375 * s2 + 0.00209 * s) * t2
        + (-0.02903 * s3 + 0.06377 * s2 - 0.03202 * s + 0.00394) * t
        + (0.11693 * s3 - 0.21196 * s2 + 0.06052 * s + 0.25886);
    float y = (0.00275 * s3 - 0.00610 * s2 + 0.00317 * s) * t2
        + (-0.04214 * s3 + 0.08970 * s2 - 0.04153 * s + 0.00516) * t
        + (0.15346 * s3 - 0.26756 * s2 + 0.06670 * s + 0.26688);
    return float2(x, y);
}

inline float3 xyYToLinearRgb(float x, float y, float bigY) {
    float capX = x * bigY / max(y, 1e-4);
    float capZ = (1.0 - x - y) * bigY / max(y, 1e-4);
    // GLSL's mat3(...) takes columns; so does float3x3(float3, float3, float3).
    return float3x3(
        float3(3.2406, -0.9689, 0.0557),
        float3(-1.5372, 1.8758, -0.2040),
        float3(-0.4986, 0.0415, 1.0570)
    ) * float3(capX, bigY, capZ);
}

inline float3 toneMap(float3 linear) {
    float luminance = dot(linear, float3(0.2126, 0.7152, 0.0722));
    if (luminance <= 0.0) return float3(0.0);
    return linear * ((luminance / (1.0 + luminance)) / luminance);
}

inline float dither(float2 pixel, float seed) {
    float noise = fract(sin(dot(pixel + seed, float2(12.9898, 78.233))) * 43758.5453);
    return (noise - 0.5) / 255.0;
}

fragment float4 sky_fragment(SkyOut in [[stage_in]], constant SkyUniforms& u [[buffer(0)]]) {
    float3 camRight = u.camRight.xyz;
    float3 camUp = u.camUp.xyz;
    float3 camForward = u.camForward.xyz;
    float3 sunDir = u.sunDir.xyz;
    float3 zenithDir = u.zenithDir.xyz;
    float2 tanHalfFov = u.params.xy;

    float3 dir = normalize(
        camForward + camRight * in.ndc.x * tanHalfFov.x + camUp * in.ndc.y * tanHalfFov.y
    );

    float cosTheta = dot(dir, zenithDir);
    float cosGamma = clamp(dot(dir, sunDir), -1.0, 1.0);
    float gamma = acos(cosGamma);
    float cosSunZenith = clamp(dot(sunDir, zenithDir), -1.0, 1.0);
    float thetaSun = acos(cosSunZenith);
    float sunAltitudeDeg = asin(cosSunZenith) * RADIANS_TO_DEGREES;
    float viewAltitudeDeg = asin(clamp(cosTheta, -1.0, 1.0)) * RADIANS_TO_DEGREES;

    // --- Daytime ---
    float t = u.params.z;
    float clampedThetaSun = min(thetaSun, 89.0 / RADIANS_TO_DEGREES);

    PerezCoeffs coeffY = {
        0.1787 * t - 1.4630, -0.3554 * t + 0.4275, -0.0227 * t + 5.3251,
        0.1206 * t - 2.5771, -0.0670 * t + 0.3703
    };
    PerezCoeffs coeffx = {
        -0.0193 * t - 0.2592, -0.0665 * t + 0.0008, -0.0004 * t + 0.2125,
        -0.0641 * t - 0.8989, -0.0033 * t + 0.0452
    };
    PerezCoeffs coeffy = {
        -0.0167 * t - 0.2608, -0.0950 * t + 0.0092, -0.0079 * t + 0.2102,
        -0.0441 * t - 1.6537, -0.0109 * t + 0.0529
    };

    float zenithY = zenithLuminance(t, clampedThetaSun);
    float2 zenithXy = zenithChromaticity(t, clampedThetaSun);

    float normY = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffY);
    float normX = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffx);
    float normYc = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffy);

    float viewCosTheta = max(cosTheta, 0.0);
    float bigY = zenithY * perez(viewCosTheta, gamma, cosGamma, coeffY) / max(normY, 1e-4);
    float chromX = zenithXy.x * perez(viewCosTheta, gamma, cosGamma, coeffx) / max(normX, 1e-4);
    float chromY = zenithXy.y * perez(viewCosTheta, gamma, cosGamma, coeffy) / max(normYc, 1e-4);

    const float EXPOSURE = 0.035;
    float3 daylight = max(xyYToLinearRgb(chromX, chromY, bigY) * EXPOSURE, float3(0.0));

    // --- Twilight ---
    float3 sunHorizontal = normalize(sunDir - zenithDir * cosSunZenith);
    float3 viewHorizontal = normalize(dir - zenithDir * cosTheta);
    float solarAlignment = dot(viewHorizontal, sunHorizontal);
    float antiSolar = max(-solarAlignment, 0.0);
    float towardSun = max(solarAlignment, 0.0);

    float daylightFactor = smoothstep(-6.0, 0.0, sunAltitudeDeg);
    float twilightFactor = smoothstep(-18.0, -5.0, sunAltitudeDeg)
        * (1.0 - smoothstep(-2.0, 2.0, sunAltitudeDeg));

    float horizonBand = exp(-abs(viewAltitudeDeg) / 9.0);
    float3 sunsetGlow = float3(0.85, 0.36, 0.12)
        * horizonBand * pow(towardSun, 2.0) * twilightFactor;

    float shadowTopDeg = clamp(-sunAltitudeDeg * 2.0, 0.0, 22.0);
    float belowShadow = 1.0 - smoothstep(shadowTopDeg - 3.0, shadowTopDeg + 1.0, viewAltitudeDeg);
    float inBelt = smoothstep(shadowTopDeg - 1.0, shadowTopDeg + 4.0, viewAltitudeDeg)
        * (1.0 - smoothstep(shadowTopDeg + 5.0, shadowTopDeg + 14.0, viewAltitudeDeg));
    float antiSolarWeight = pow(antiSolar, 1.5) * twilightFactor
        * step(-0.5, viewAltitudeDeg);

    float3 earthShadow = float3(0.05, 0.06, 0.11) * belowShadow * antiSolarWeight;
    float3 beltOfVenus = float3(0.55, 0.30, 0.34) * inBelt * antiSolarWeight;

    float3 twilightWash = float3(0.035, 0.055, 0.10) * twilightFactor
        * mix(0.4, 1.0, horizonBand);

    float3 linear = daylight * daylightFactor
        + sunsetGlow + earthShadow + beltOfVenus + twilightWash;

    // --- Below the horizon, dim ---
    const float BELOW_HORIZON_DIM = 0.14;
    // GLSL writes smoothstep(0.0, -4.0, altitude); reversed edges are undefined in MSL, so this is
    // the identical value written with ordered edges (the Hermite curve has h(1 - s) = 1 - h(s)).
    float below = 1.0 - smoothstep(-4.0, 0.0, viewAltitudeDeg);
    linear *= mix(1.0, BELOW_HORIZON_DIM, below);

    // GLSL's gl_FragCoord.xy is [[position]].xy here. Its origin differs (top-left, not
    // bottom-left), which only moves the dither pattern.
    float3 srgb = pow(toneMap(linear), float3(1.0 / 2.2)) + dither(in.position.xy, u.params.w);
    return float4(clamp(srgb, 0.0, 1.0), 1.0);
}
