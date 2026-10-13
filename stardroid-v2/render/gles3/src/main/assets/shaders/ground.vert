// The ground shell, as a full-screen quad rather than geometry.
//
// Same trick as sky.vert and for the same reason: the ground is a continuous function of view
// direction, so there is nothing to tessellate. GLES1 has to approximate it with a ring mesh
// whose ring altitudes are chosen to trace the falloff curve piecewise; here the curve is
// evaluated per pixel and the mesh disappears.
out vec2 vNdc;

void main() {
    vec2 corner = unitQuadCorner(gl_VertexID);
    vNdc = corner * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 0.0, 1.0);
}
