// The camera dimmer: a full-screen quad drawn under the map while the background is
// transparent, darkening the video plane below without touching anything drawn on top.
out vec2 vNdc;

void main() {
    vec2 corner = unitQuadCorner(gl_VertexID);
    vNdc = corner * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 0.0, 1.0);
}
