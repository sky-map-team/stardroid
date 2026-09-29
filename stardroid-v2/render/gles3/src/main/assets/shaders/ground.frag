// The translucent ground below the horizon.
//
// The ramp itself lives in common.glsl as groundAlpha and friends, transcribed from `GroundRamp`
// in :render:api -- that stays the golden reference and is unit-tested there, and the conformance
// test samples the shader against it. Keep the two in step: transcription drift, not a wrong
// algorithm, is the failure mode that arrangement exists to catch, and a constant changed on one
// side only would stay invisible until someone looked at a sunset.
in vec2 vNdc;

uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamForward;
uniform vec2 uTanHalfFov;

uniform vec3 uSunDir;
uniform vec3 uZenithDir;

uniform vec3 uGroundNightColor;
uniform vec3 uGroundDayColor;
uniform float uGroundOpacity;

out vec4 fragColor;

void main() {
    if (uGroundOpacity <= 0.0) discard;

    vec3 dir = normalize(
        uCamForward + uCamRight * vNdc.x * uTanHalfFov.x + uCamUp * vNdc.y * uTanHalfFov.y
    );
    float viewAltitudeDeg = degrees(asin(clamp(dot(dir, uZenithDir), -1.0, 1.0)));
    float sunAltitudeDeg = degrees(asin(clamp(dot(uSunDir, uZenithDir), -1.0, 1.0)));

    // Everything well above the horizon is sky, and discarding rather than writing a zero-alpha
    // pixel keeps the blend unit out of it for the majority of a typical frame. The edge ramp
    // straddles zero, so the test has to clear it or the blend would be cut in half.
    float rampDeg = groundEdgeRampDeg(viewAltitudeDeg);
    if (viewAltitudeDeg > rampDeg) discard;

    vec3 color = mix(uGroundNightColor, uGroundDayColor, groundDaylight(sunAltitudeDeg));
    fragColor = vec4(color, groundAlpha(viewAltitudeDeg, uGroundOpacity, rampDeg));
}
