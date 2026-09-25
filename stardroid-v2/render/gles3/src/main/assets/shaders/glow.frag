precision mediump float;

in vec4 vColor;
uniform float uNightMode;
out vec4 fragColor;

void main() {
    fragColor = applyNightMode(vColor, uNightMode);
}
