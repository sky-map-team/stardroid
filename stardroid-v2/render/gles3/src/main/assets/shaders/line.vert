// Plain GL_LINE_STRIP. Width stays glLineWidth, which is core in GL ES 3.0.
//
// Replacing it with vertex-shader quad extrusion is the obvious move and is deliberately
// rejected (render-gles3.md §3.1): our lines are translucent — the grid is 8% alpha — and every
// great circle is subdivided into ~72 segments, so independently extruded quads would
// double-blend at every joint and read as beaded. If a device is ever measured clamping the
// width, the answer is a mitered triangle strip with shared joint vertices, never naive quads.
layout(location = 0) in vec3 aPos;

uniform mat4 uViewProj;

void main() {
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
