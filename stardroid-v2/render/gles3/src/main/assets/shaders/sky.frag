precision highp float;

in vec2 vNdc;

// Camera basis in celestial coordinates, and the tangents of the half-FOV along each screen
// axis, so every pixel can reconstruct its own view direction without a matrix inverse.
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamForward;
uniform vec2 uTanHalfFov;

uniform vec3 uSunDir;
uniform vec3 uZenithDir;
uniform float uTurbidity;
// Animates the dither pattern so it cannot read as a fixed screen-door texture.
uniform float uDitherSeed;

out vec4 fragColor;

const float PI = 3.14159265359;

// ---------------------------------------------------------------------------------------------
// Preetham et al., "A Practical Analytic Model for Daylight" (SIGGRAPH 1999).
//
// Roughly thirty ALU operations for the whole daytime sky, and it produces the characteristic
// behaviour for free rather than by hand-tuning: the bright circumsolar aureole, the darker band
// about 90 degrees from the sun, and the brightening toward the horizon. The GLES1 ramp gestures
// at daylight; this is a sky.
// ---------------------------------------------------------------------------------------------

/** The five Perez distribution coefficients for one of Y, x or y. */
struct PerezCoeffs {
    float a;
    float b;
    float c;
    float d;
    float e;
};

/** The Perez luminance/chromaticity distribution, evaluated for one set of five coefficients. */
float perez(float cosTheta, float gamma, float cosGamma, PerezCoeffs c) {
    return (1.0 + c.a * exp(c.b / max(cosTheta, 0.01)))
        * (1.0 + c.c * exp(c.d * gamma) + c.e * cosGamma * cosGamma);
}

/** Zenith absolute luminance in cd/m^2, Preetham appendix A.2. */
float zenithLuminance(float turbidity, float thetaSun) {
    float chi = (4.0 / 9.0 - turbidity / 120.0) * (PI - 2.0 * thetaSun);
    return (4.0453 * turbidity - 4.9710) * tan(chi) - 0.2155 * turbidity + 2.4192;
}

/** Zenith chromaticity (x, y), Preetham appendix A.2 — the polynomials in T and thetaSun. */
vec2 zenithChromaticity(float turbidity, float thetaSun) {
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
    return vec2(x, y);
}

/** CIE xyY to linear sRGB. */
vec3 xyYToLinearRgb(float x, float y, float bigY) {
    float capX = x * bigY / max(y, 1e-4);
    float capZ = (1.0 - x - y) * bigY / max(y, 1e-4);
    return mat3(
        3.2406, -0.9689, 0.0557,
        -1.5372, 1.8758, -0.2040,
        -0.4986, 0.0415, 1.0570
    ) * vec3(capX, bigY, capZ);
}

/**
 * Compresses brightness while leaving colour alone.
 *
 * Applying `x / (1 + x)` per channel — the obvious form — desaturates as it compresses, because
 * it pulls the brightest channel down hardest. A sky bright enough to need tone mapping is
 * exactly a sky whose blue then drains out of it, which is what made midday come out
 * grey. Scaling by a luminance ratio compresses the same amount and keeps the chromaticity the
 * Preetham model went to the trouble of computing.
 */
vec3 toneMap(vec3 linear) {
    float luminance = dot(linear, vec3(0.2126, 0.7152, 0.0722));
    if (luminance <= 0.0) return vec3(0.0);
    return linear * ((luminance / (1.0 + luminance)) / luminance);
}

/**
 * An ordered-dither offset of roughly one 8-bit step.
 *
 * A smooth gradient across a 1440p panel in 8 bits per channel bands visibly — it is the single
 * most obvious "this is computer graphics" artifact a sky can have, and three lines remove it.
 */
float dither(vec2 fragment) {
    float noise = fract(sin(dot(fragment + uDitherSeed, vec2(12.9898, 78.233))) * 43758.5453);
    return (noise - 0.5) / 255.0;
}

void main() {
    vec3 dir = normalize(
        uCamForward + uCamRight * vNdc.x * uTanHalfFov.x + uCamUp * vNdc.y * uTanHalfFov.y
    );

    float cosTheta = dot(dir, uZenithDir);          // view zenith angle
    float cosGamma = clamp(dot(dir, uSunDir), -1.0, 1.0);
    float gamma = acos(cosGamma);
    float cosSunZenith = clamp(dot(uSunDir, uZenithDir), -1.0, 1.0);
    float thetaSun = acos(cosSunZenith);
    float sunAltitudeDeg = degrees(asin(cosSunZenith));
    float viewAltitudeDeg = degrees(asin(clamp(cosTheta, -1.0, 1.0)));

    // --- Daytime ------------------------------------------------------------------------------
    // Preetham is only defined with the sun above the horizon; below it the model is clamped and
    // faded out, and the twilight terms below take over.
    float t = uTurbidity;
    float clampedThetaSun = min(thetaSun, radians(89.0));

    PerezCoeffs coeffY = PerezCoeffs(
        0.1787 * t - 1.4630, -0.3554 * t + 0.4275, -0.0227 * t + 5.3251,
        0.1206 * t - 2.5771, -0.0670 * t + 0.3703
    );
    PerezCoeffs coeffx = PerezCoeffs(
        -0.0193 * t - 0.2592, -0.0665 * t + 0.0008, -0.0004 * t + 0.2125,
        -0.0641 * t - 0.8989, -0.0033 * t + 0.0452
    );
    PerezCoeffs coeffy = PerezCoeffs(
        -0.0167 * t - 0.2608, -0.0950 * t + 0.0092, -0.0079 * t + 0.2102,
        -0.0441 * t - 1.6537, -0.0109 * t + 0.0529
    );

    float zenithY = zenithLuminance(t, clampedThetaSun);
    vec2 zenithXy = zenithChromaticity(t, clampedThetaSun);

    float normY = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffY);
    float normX = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffx);
    float normYc = perez(1.0, clampedThetaSun, cos(clampedThetaSun), coeffy);

    float viewCosTheta = max(cosTheta, 0.0);
    float bigY = zenithY * perez(viewCosTheta, gamma, cosGamma, coeffY) / max(normY, 1e-4);
    float chromX = zenithXy.x * perez(viewCosTheta, gamma, cosGamma, coeffx) / max(normX, 1e-4);
    float chromY = zenithXy.y * perez(viewCosTheta, gamma, cosGamma, coeffy) / max(normYc, 1e-4);

    // Preetham's absolute luminance is far brighter than a display; this exposure is the one
    // free parameter, chosen so a midday zenith lands near the GLES1 ramp's blue.
    const float EXPOSURE = 0.035;
    vec3 daylight = max(xyYToLinearRgb(chromX, chromY, bigY) * EXPOSURE, vec3(0.0));

    // --- Twilight -----------------------------------------------------------------------------
    // The regime v1 never attempted, and the interesting one. As the sun drops through civil,
    // nautical and astronomical twilight the sky does something specific and nameable, and it
    // falls out almost for free once the sky is a function of direction.
    vec3 sunHorizontal = normalize(uSunDir - uZenithDir * cosSunZenith);
    vec3 viewHorizontal = normalize(dir - uZenithDir * cosTheta);
    // 1 toward the sun, -1 toward the anti-solar point.
    float solarAlignment = dot(viewHorizontal, sunHorizontal);
    float antiSolar = max(-solarAlignment, 0.0);
    float towardSun = max(solarAlignment, 0.0);

    // How much daylight survives: full above the horizon, gone by the end of civil twilight.
    float daylightFactor = smoothstep(-6.0, 0.0, sunAltitudeDeg);
    // The twilight terms fade in as the sun sets and are gone within a couple of degrees above
    // the horizon. The upper taper matters: without it the warm horizon band sits on top of a
    // fully lit Preetham sky for the first several degrees of the morning, which desaturates
    // the whole sky to a muddy olive instead of letting it go blue.
    float twilightFactor = smoothstep(-18.0, -5.0, sunAltitudeDeg)
        * (1.0 - smoothstep(-2.0, 2.0, sunAltitudeDeg));

    // The warm band above where the sun went down, strongest just after sunset and hugging the
    // horizon.
    // Peaks at the horizon and falls off in *both* directions. Measuring from
    // `max(altitude, 0)` made every direction below the horizon score a full 1.0, so the warm
    // band did not hug the horizon at all — it flooded the entire lower hemisphere.
    float horizonBand = exp(-abs(viewAltitudeDeg) / 9.0);
    vec3 sunsetGlow = vec3(0.85, 0.36, 0.12)
        * horizonBand * pow(towardSun, 2.0) * twilightFactor;

    // Earth's own shadow, rising in the east as the sun sets: a dark blue-grey wedge whose top
    // climbs roughly twice as fast as the sun descends. Above it sits the Belt of Venus, the
    // pink backscatter band — a real, nameable, teachable thing that almost no mobile
    // planetarium draws, and here it is two smoothsteps.
    float shadowTopDeg = clamp(-sunAltitudeDeg * 2.0, 0.0, 22.0);
    float belowShadow = 1.0 - smoothstep(shadowTopDeg - 3.0, shadowTopDeg + 1.0, viewAltitudeDeg);
    float inBelt = smoothstep(shadowTopDeg - 1.0, shadowTopDeg + 4.0, viewAltitudeDeg)
        * (1.0 - smoothstep(shadowTopDeg + 5.0, shadowTopDeg + 14.0, viewAltitudeDeg));
    float antiSolarWeight = pow(antiSolar, 1.5) * twilightFactor
        * step(-0.5, viewAltitudeDeg);

    vec3 earthShadow = vec3(0.05, 0.06, 0.11) * belowShadow * antiSolarWeight;
    vec3 beltOfVenus = vec3(0.55, 0.30, 0.34) * inBelt * antiSolarWeight;

    // A dim residual blue across the whole sky through twilight, so the transition to night is a
    // fade rather than a cut.
    vec3 twilightWash = vec3(0.035, 0.055, 0.10) * twilightFactor
        * mix(0.4, 1.0, horizonBand);

    vec3 linear = daylight * daylightFactor
        + sunsetGlow + earthShadow + beltOfVenus + twilightWash;

    // Below the horizon, dim.
    //
    // v2 deliberately lets you look through the Earth at the sky beneath it, so there is no
    // ground to draw — but the scattering model is only defined above the horizon, and
    // clamping its input leaves the whole lower hemisphere painted flat at the horizon's
    // brightness, which is the brightest part of the sky. The result was a large glowing wedge
    // under the horizon line that dominated every daytime frame.
    //
    // Dimming it keeps the "look through the Earth" view while saying plainly which side of the
    // horizon you are on. This is a placeholder for the shaded translucent ground in §7.2,
    // which is the real answer.
    const float BELOW_HORIZON_DIM = 0.14;
    float below = smoothstep(0.0, -4.0, viewAltitudeDeg);
    linear *= mix(1.0, BELOW_HORIZON_DIM, below);

    // Everything above is computed in linear space; tone map, convert to sRGB, and dither.
    vec3 srgb = pow(toneMap(linear), vec3(1.0 / 2.2)) + dither(gl_FragCoord.xy);
    fragColor = vec4(clamp(srgb, 0.0, 1.0), 1.0);
}
