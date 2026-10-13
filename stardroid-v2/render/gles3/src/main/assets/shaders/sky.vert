// The sky dome, as a full-screen quad rather than geometry.
//
// GLES1 draws an eight-band, ten-step sphere and Gouraud-interpolates a hardcoded ramp across
// it, because per-vertex colour is the only colour source a fixed-function pipeline has. The
// band count is therefore a rendering artifact, not a description of the sky. Here the sky is
// evaluated per pixel and the mesh disappears entirely.
out vec2 vNdc;

void main() {
    vec2 corner = unitQuadCorner(gl_VertexID);
    vNdc = corner * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 0.0, 1.0);
}
