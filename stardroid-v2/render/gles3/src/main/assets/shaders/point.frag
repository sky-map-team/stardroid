// An analytic anti-aliased disc, replacing GL_POINT_SMOOTH.
//
// Fixed-function point smoothing is optional in GL ES: GL_SMOOTH_POINT_SIZE_RANGE may be [1, 1],
// in which case every star on that device collapses to a single aliased pixel and nothing tells
// us (D31). Computing coverage here removes the hazard entirely — the disc is the same shape on
// every device because we draw it ourselves.
precision mediump float;

in vec4 vColor;
out vec4 fragColor;

void main() {
    // 0 at the centre of the point sprite, 1 at the inscribed circle's edge.
    float d = length(gl_PointCoord - vec2(0.5)) * 2.0;
    // One pixel of feathering, measured in the same units as d so it holds at any point size.
    float aa = max(fwidth(d), 1e-4);
    float coverage = 1.0 - smoothstep(1.0 - aa, 1.0, d);
    if (coverage <= 0.0) discard;
    fragColor = vec4(vColor.rgb, vColor.a * coverage);
}
