#version 150

// A full-screen quad given straight in clip space.
in vec3 Position;

out vec2 screenPos;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    screenPos = Position.xy;
}
