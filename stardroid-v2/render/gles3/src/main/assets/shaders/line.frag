precision mediump float;

uniform vec4 uColor;
uniform float uNightMode;

out vec4 fragColor;

void main() {
    fragColor = applyNightMode(uColor, uNightMode);
}
