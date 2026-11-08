#version 150

// One triangle that covers the whole screen, made from the vertex index alone (no vertex data).
out vec2 ndc;

void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    ndc = corner * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
