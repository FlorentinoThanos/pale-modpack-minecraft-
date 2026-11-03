#version 150

// The Pale fog laid over a shader pack's finished frame. The Pale is open sea, so the distance a pixel looks to is
// where its view ray meets the water; rays at or above the horizon see the sky and get the full fog.

uniform mat4 PaleInvViewProj; // inverse of projection * camera rotation (camera-relative, no translation)
uniform float PaleEyeAboveSea;
uniform vec2 PaleFogRange;    // where the fog starts / where it is complete, in blocks
uniform vec4 PaleFogColor;    // rgb, and a = how far the Pale grey has taken over

in vec2 screenPos;

out vec4 fragColor;

void main() {
    vec4 farPoint = PaleInvViewProj * vec4(screenPos, 1.0, 1.0);
    vec3 ray = normalize(farPoint.xyz / farPoint.w);
    float distanceSeen = ray.y < -1.0e-4 ? PaleEyeAboveSea / -ray.y : 1.0e9;
    float fog = clamp((distanceSeen - PaleFogRange.x) / (PaleFogRange.y - PaleFogRange.x), 0.0, 1.0);
    fragColor = vec4(PaleFogColor.rgb, fog * PaleFogColor.a);
}
