#version 150

// Lays the steam (premultiplied by its opacity) over the shader pack's finished frame.
uniform sampler2D Steam;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(Steam, ivec2(gl_FragCoord.xy), 0);
}
