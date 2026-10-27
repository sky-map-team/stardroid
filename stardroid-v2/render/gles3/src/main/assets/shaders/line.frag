precision mediump float;

uniform vec4 uColor;
uniform bool uNightMode;

out vec4 fragColor;

void main() {
    fragColor = applyNightMode(uColor, uNightMode);
}
