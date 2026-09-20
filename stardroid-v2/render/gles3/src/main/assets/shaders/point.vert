// Stars and fixed points. One VBO, one draw call for a whole layer.
//
// The GLES1 backend sorts points into runs of equal size and issues one draw call per run,
// because glPointSize is per-draw-call state there. Here size is a per-vertex output, so the
// runs disappear. Colour and size are baked at build time (they depend only on the scene, per
// the build/draw rule); the magnitude limit and night mode are uniforms, so changing either
// re-filters the sky without touching a buffer — which is exactly what invalidates GLES1's.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aColor;
layout(location = 2) in float aSizeDp;
layout(location = 3) in float aMagnitude;

uniform mat4 uViewProj;
uniform float uDensity;
uniform float uMagnitudeLimit;
uniform float uNightMode;

out vec4 vColor;

void main() {
    gl_Position = uViewProj * vec4(aPos, 1.0);
    gl_PointSize = aSizeDp * uDensity;
    float alpha = aColor.a * magnitudeAlpha(aMagnitude, uMagnitudeLimit);
    vColor = applyNightMode(vec4(aColor.rgb, alpha), uNightMode);
}
