#version 150

// The Pale as steam, for shader packs (PaleSteam). Each pixel's view ray is marched through the fog from the camera
// to whatever the pixel shows: the density at every point is what the camera fog would be there (PaleFog#visibility),
// so the haze is soft and thickens out into the Pale, ragged and rolling at its edge, and clears high above it.
// Written premultiplied by its opacity, for laying over the pack's finished frame.

uniform sampler2D SceneDepth;  // the level's depth buffer
uniform sampler2D DhDepth;     // Distant Horizons' depth buffer, read only if UseDh is set
uniform sampler2D Field;       // r: how far out (effective radius) past where the fog starts; g: how far, sideways, the nearest fog may be
uniform sampler2D Noise;       // r: value noise; g: the same, offset by (37, 17) (the next slice up)

uniform mat4 InvViewProj;      // clip space to camera-relative world space
uniform mat4 InvDhViewProj;    // the same for Distant Horizons' projection
uniform int UseDh;
uniform vec2 ScreenSize;

uniform float CameraY;
uniform vec2 FieldOrigin;      // the camera's position from the field's corner, in blocks
uniform vec2 FieldScale;       // x: 1 / the field's size in blocks; y: the size of one of its cells
uniform vec3 BroadOffset;      // the camera's position, moved by the drift and wrapped to the noise's period, per octave
uniform vec3 FineOffset;

uniform vec4 Radii;            // x: length of the approach; y: innerRadius past the fog's start; z: outerRadius - innerRadius; w: depth of the ragged edge
uniform vec4 Visibility;       // x: normal visibility; y: fogEntryVisibility; z: fogFullVisibility; w: density (optical depth over one visibility)
uniform vec3 Heights;          // x: the sea's surface; y: where the air starts to clear; z: where it is clear
uniform vec3 FogColor;         // the Pale grey as lit right now
uniform vec3 FarColor;         // the normal fog colour, which far-off steam takes on a little

in vec2 ndc;

out vec4 fragColor;

const int MAX_STEPS = 96;
const int REFLECTION_STEPS = 40;
const float MAX_DISTANCE = 12000.0;
/** Optical depth per step in the fog: smaller is smoother and slower. */
const float STEP_DEPTH = 0.3;
const float MIN_STEP = 0.35;
/** Noise cell sizes, in blocks: big billows, and finer curls on them. */
const float BROAD_CELL = 88.0;
const float FINE_CELL = 29.0;

float smooth01(float t) {
    t = clamp(t, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

/** Smooth 3D value noise from one lookup: two slices of 2D noise, one above the other, blended. */
float noise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = cell.xz + vec2(37.0, 17.0) * cell.y + f.xz;
    vec2 slices = texture(Noise, (uv + 0.5) / 256.0).rg;
    return mix(slices.x, slices.y, f.y);
}

/** {@return 0 to 1}: how thick the steam is puffed up here (camera-relative position). */
float puffs(vec3 p) {
    float broad = noise3((BroadOffset + p) * (1.0 / BROAD_CELL));
    float fine = noise3((FineOffset + p) * (1.0 / FINE_CELL));
    // Value noise mostly stays near 0.5; stretch it so the puffs read.
    return smooth01((broad * 0.7 + fine * 0.3 - 0.28) / 0.44);
}

/** How far you can see at this many blocks past where the fog starts: PaleFog#visibility. */
float visibilityAt(float past) {
    if (past < Radii.y) {
        float from = max(Visibility.x, Visibility.y);
        return from * pow(Visibility.y / from, smooth01(past / Radii.x));
    }
    float progress = min(1.0, (past - Radii.y) / Radii.z);
    return Visibility.y * pow(Visibility.z / Visibility.y, progress);
}

/** {@return the steam's density (per block) at this height, this far out (past the fog's start) and this puffy} */
float densityAt(float past, float y, float puff) {
    // The bank's front billows in and out.
    past += (puff - 0.5) * Radii.w;
    if (past <= 0.0) {
        return 0.0;
    }
    // High above the Pale the air clears, as if back at the start of the approach (PaleAltitude), with a lumpy top.
    float lumpy = y + (puff - 0.5) * 36.0;
    past *= smooth01((Heights.z - lumpy) / (Heights.z - Heights.y));
    float approach = smooth01(past / Radii.x);
    if (approach <= 0.0) {
        return 0.0;
    }
    return Visibility.w * approach / visibilityAt(past) * mix(0.4, 1.6, puff);
}

/** The steam's colour: brighter toward the top of the bank where the sun gets in, the thick puffs a little shaded. */
vec3 colorAt(float y, float puff, float distance) {
    float lift = smooth01((y - 200.0) / 290.0);
    vec3 color = FogColor * (mix(1.0, 1.25, lift) * mix(1.04, 0.93, puff));
    // Far off it takes on a little of the sky.
    color = mix(color, FarColor, 0.3 * smooth01((distance - 400.0) / 2500.0));
    return min(color, vec3(1.0));
}

/** {@return how far along its view ray this pixel shows something solid (or water), or a long way for the sky} */
float sceneDistance() {
    float distance = 1.0e7;
    float depth = texelFetch(SceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (depth < 1.0) {
        vec4 p = InvViewProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        distance = length(p.xyz / p.w);
    }
    if (UseDh != 0) {
        vec2 size = vec2(textureSize(DhDepth, 0));
        float dhDepth = texelFetch(DhDepth, ivec2(gl_FragCoord.xy * size / ScreenSize), 0).r;
        if (dhDepth < 1.0) {
            vec4 p = InvDhViewProj * vec4(ndc, dhDepth * 2.0 - 1.0, 1.0);
            distance = min(distance, length(p.xyz / p.w));
        }
    }
    return distance;
}

/** A fixed, even scatter of 0 to 1 per pixel, so the steps start staggered instead of in bands. */
float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

/**
 * Marches a ray from {@code origin} (camera-relative) through the steam, from {@code start} to {@code end} along it.
 * {@return the steam's light along it (premultiplied by its opacity), and how much of what's behind gets through}
 */
vec4 march(vec3 origin, vec3 dir, float start, float end, float travelled, float jitter, int maxSteps) {
    float sideways = max(length(dir.xz), 1.0e-3);
    float transmittance = 1.0;
    vec3 light = vec3(0.0);
    float t = start;
    float dt = 6.0;
    for (int i = 0; i < maxSteps; i++) {
        if (t >= end || transmittance < 0.004) {
            break;
        }
        dt = min(dt, end - t);
        float at = t + dt * jitter;
        vec3 p = origin + dir * at;
        vec2 field = texture(Field, (FieldOrigin + p.xz) * FieldScale.x).rg;
        // Far from any fog (over an island): jump most of the way to it.
        float clear = field.g - 1.5 * FieldScale.y;
        if (clear > 0.0) {
            t = at + clear / sideways;
            dt = 6.0;
            continue;
        }
        float y = CameraY + p.y;
        float puff = puffs(p);
        float sigma = densityAt(field.r, y, puff);
        if (sigma > 0.0) {
            float absorbed = 1.0 - exp(-sigma * dt);
            light += transmittance * absorbed * colorAt(y, puff, travelled + at);
            transmittance *= 1.0 - absorbed;
        }
        t += dt;
        // Steps sized to the density, and longer the further out (distant detail can't be seen anyway).
        dt = sigma > 0.0
                ? clamp(STEP_DEPTH / sigma, MIN_STEP, clamp(8.0 + (travelled + t) * 0.08, 8.0, 64.0))
                : clamp((travelled + t) * 0.03, 6.0, 48.0);
    }
    return vec4(light, transmittance);
}

void main() {
    vec4 farPoint = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(farPoint.xyz / farPoint.w);
    float jitter = interleavedGradientNoise(gl_FragCoord.xy);

    // Only the part of the ray between the sea and where the air is clear can be in the steam.
    float scene = sceneDistance();
    float end = min(scene, MAX_DISTANCE);
    float sea = CameraY > Heights.x && dir.y < -1.0e-4 ? (CameraY - Heights.x) / -dir.y : MAX_DISTANCE * 2.0;
    // The ray ends on the sea: the water itself, or open sea past the chunks that are drawn.
    bool water = sea < MAX_DISTANCE && scene > sea - max(1.0, 0.3 / -dir.y);
    end = min(end, sea);
    float start = 0.0;
    if (CameraY > Heights.z) {
        start = dir.y < -1.0e-4 ? (CameraY - Heights.z) / -dir.y : MAX_DISTANCE;
    } else if (dir.y > 1.0e-4) {
        end = min(end, (Heights.z - CameraY) / dir.y);
    }
    vec4 steam = start < end ? march(vec3(0.0), dir, start, end, 0.0, jitter, MAX_STEPS) : vec4(0.0, 0.0, 0.0, 1.0);

    if (water && steam.a > 0.004) {
        // The shader pack's water only reflects its own sky, which would show as a bright strip in front of the bank:
        // let it reflect the steam, as much as water reflects at this angle.
        vec3 up = vec3(dir.x, -dir.y, dir.z);
        float rise = (Heights.z - Heights.x) / up.y;
        vec4 mirrored = march(dir * end, up, 0.0, min(rise, MAX_DISTANCE), end, jitter, REFLECTION_STEPS);
        float fresnel = 0.02 + 0.98 * pow(1.0 - up.y, 5.0);
        steam.rgb += steam.a * fresnel * mirrored.rgb;
        steam.a *= 1.0 - fresnel * (1.0 - mirrored.a);
    }

    float alpha = 1.0 - steam.a;
    if (alpha < 1.0 / 512.0) {
        fragColor = vec4(0.0);
        return;
    }
    // Half a step of dither, against banding in the 8-bit target.
    float dither = (jitter - 0.5) / 255.0;
    fragColor = clamp(vec4(steam.rgb + dither, alpha + dither), 0.0, 1.0);
}
