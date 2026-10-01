// Instanced screen-space quads: icons, label glyphs, and the small "this is tappable" marker.
//
// One shader, three configurations, because they differ only in what fills the quad. GLES1
// issues a glPushMatrix / glScalef / glDrawArrays per sprite; here the whole layer's sprites are
// one instanced draw and the quad itself comes from gl_VertexID, so there is no vertex buffer.
layout(location = 0) in vec2 aCenterPx;
layout(location = 1) in vec2 aSizePx;
layout(location = 2) in vec2 aUv0;
layout(location = 3) in vec2 aUv1;
layout(location = 4) in vec4 aTint;
layout(location = 5) in float aMode;

// Pixel dimensions of the viewport; the quad is positioned directly in pixels and converted to
// clip space here, which is the same bottom-left-origin orthographic setup GLES1 pushes.
uniform vec2 uViewportPx;

out vec2 vUv;
out vec4 vTint;
out vec2 vLocal;
flat out int vMode;

void main() {
    vec2 corner = unitQuadCorner(gl_VertexID);
    vec2 posPx = aCenterPx + (corner - vec2(0.5)) * aSizePx;
    gl_Position = vec4(posPx / uViewportPx * 2.0 - 1.0, 0.0, 1.0);
    vUv = mix(aUv0, aUv1, corner);
    vTint = aTint;
    vLocal = corner;
    vMode = int(aMode + 0.5);
}
