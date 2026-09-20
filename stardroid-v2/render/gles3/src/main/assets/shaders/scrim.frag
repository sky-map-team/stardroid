precision mediump float;

in vec2 vNdc;
uniform float uOpacity;
out vec4 fragColor;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, uOpacity);
}
