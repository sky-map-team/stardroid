// The horizon glow: a filled mesh of concentric vertex rings, blended additively.
//
// A faithful port of the GLES1 behaviour — colour interpolated across each band, including
// alpha. The producer still submits eight rings, which exist only to piecewise-approximate an
// exponential falloff that a fragment shader could evaluate directly; that is a Part B change
// (it needs the producer to stop encoding a renderer limitation) and deliberately not made here,
// so the two backends draw the same glow.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aColor;

uniform mat4 uViewProj;

out vec4 vColor;

void main() {
    gl_Position = uViewProj * vec4(aPos, 1.0);
    vColor = aColor;
}
