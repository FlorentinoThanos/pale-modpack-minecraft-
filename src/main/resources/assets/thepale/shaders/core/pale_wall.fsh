#version 150

// The Pale fog wall. Deliberately takes no fog uniforms: the wall has to stay visible past the terrain fog.

uniform vec4 ColorModulator;
uniform vec2 PaleFadeRange;  // horizontal distance from the camera where the wall starts to fade / is gone
uniform vec3 PaleDriftBroad; // drift offsets in noise cells, wrapped to NOISE_PERIOD on the CPU
uniform vec3 PaleDriftFine;
uniform vec3 PaleCameraOffset; // camera position relative to the wall centre (y is absolute)
uniform float PaleInside;      // 0 outside the inner radius, 1 from 60 blocks past it
uniform vec2 PaleWallCenter;   // centre of the island this wall goes round

in vec3 cameraRelPos;
in vec3 wallPos;
in vec2 wallCoord;           // x: 0 at the bottom of the wall, 1 at the top. y: layer index
in vec4 vertexColor;

out vec4 fragColor;

const float NOISE_PERIOD = 256.0; // must match PaleWallRenderer.NOISE_PERIOD
const float HUGE_CELL = 128.0;    // big cloud masses
const float SEA_LEVEL = 63.0;
const float INSIDE_NEAR_FADE_END = 20.0; // once inside the Pale, clouds show from this close
const float WATER_FADE_HEIGHT = 40.0; // blocks above the sea over which the sheets fade back in
const float WATER_MIN_ALPHA = 0.2;    // sheet strength right at the water surface
const float BROAD_CELL = 48.0;    // blocks per noise cell
const float FINE_CELL = 16.0;
const float NEAR_FADE_START = 6.0; // blocks from the camera where the sheets are fully transparent
const float NEAR_FADE_END = 160.0;  // blocks from the camera where they are back to full strength
const float GRAZING_START = 0.02;   // |cos| between wall normal and view: fully transparent below this
const float GRAZING_END = 0.7;      // full strength above this

// Wrapping the lattice makes the noise repeat every NOISE_PERIOD cells, so the CPU can wrap the drift offsets
// (keeping them small enough for float precision) without the pattern ever jumping.
float hash(vec3 cell) {
    vec3 p = fract(mod(cell, NOISE_PERIOD) * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(cell), hash(cell + vec3(1.0, 0.0, 0.0)), u.x),
            mix(hash(cell + vec3(0.0, 1.0, 0.0)), hash(cell + vec3(1.0, 1.0, 0.0)), u.x), u.y),
        mix(mix(hash(cell + vec3(0.0, 0.0, 1.0)), hash(cell + vec3(1.0, 0.0, 1.0)), u.x),
            mix(hash(cell + vec3(0.0, 1.0, 1.0)), hash(cell + vec3(1.0, 1.0, 1.0)), u.x), u.y),
        u.z);
}

// Octave frequencies double exactly, so the sum keeps the NOISE_PERIOD repeat.
float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amplitude * valueNoise(p);
        p = p * 2.0 + vec3(5.2, 1.3, 7.7);
        amplitude *= 0.5;
    }
    return sum / 0.875;
}

void main() {
    // Different noise per layer, so the cylinders don't line up into one pattern.
    vec3 seed = wallCoord.y * vec3(17.0, 5.0, 29.0);
    float broad = fbm(wallPos / BROAD_CELL - PaleDriftBroad + seed);
    float fine = valueNoise(wallPos / FINE_CELL - PaleDriftFine + seed);

    // Big slow cloud masses on top of the billows.
    float huge = fbm(wallPos / HUGE_CELL - PaleDriftBroad + seed * 0.5); // same drift as broad, so the wrap stays seamless

    // Cloudy: thick masses with thin gaps between them, instead of an even sheet.
    float cloud = smoothstep(0.3, 0.75, huge * 0.45 + broad * 0.4 + fine * 0.15);
    float density = mix(0.12, 1.9, cloud);

    // Shade the clouds: thick parts a little darker and cooler, thin parts lighter, so it reads as murk, not paint.
    vec3 shade = mix(vec3(1.05, 1.05, 1.04), vec3(0.86, 0.87, 0.89), cloud);

    // Fade out toward the top, with the billows pushing the fade unevenly downwards; feather the very bottom
    // too so it doesn't end in a hard line where it's exposed (oceans, ravines).
    float vertical = (1.0 - smoothstep(0.25, 1.0, wallCoord.x + broad * 0.15)) * smoothstep(0.0, 0.03, wallCoord.x);

    // Start the distance fade earlier, so the far side of the ring dissolves over a long stretch.
    float distanceFade = 1.0 - smoothstep(PaleFadeRange.x * 0.4, PaleFadeRange.y, length(cameraRelPos.xz));

    // Grazing-angle fade: where the view ray runs almost along a sheet (looking along the wall from near it),
    // the hit distance changes very fast with angle and shows as a hard vertical edge. Fade those parts out
    // smoothly, with the noise breaking up the edge.
    vec2 wallNormal = normalize(wallPos.xz - PaleWallCenter);
    vec2 viewDir = normalize(cameraRelPos.xz);
    float facing = abs(dot(wallNormal, viewDir));
    float grazingFade = smoothstep(GRAZING_START, GRAZING_END, facing + (broad - 0.5) * 0.15);

    // Fade the sheets out close to the camera, so the line where a sheet cuts the water (or the player) is never
    // seen up close. Past ~96 blocks the camera fog is thick enough to hide the cut. Ragged with the noise so the
    // fade edge itself doesn't read as a circle.
    float nearDist = length(cameraRelPos) + (broad - 0.5) * 24.0;
    // Outside the Pale the sheets stay faded near the player (hides where they cut the water). Inside it the
    // camera fog is already grey, so let the clouds come right up to the player and drift past.
    float inside = PaleInside; // worked out on the CPU, where the island's outline is known
    float nearEnd = mix(NEAR_FADE_END, INSIDE_NEAR_FADE_END, inside);
    float nearFade = smoothstep(min(NEAR_FADE_START, nearEnd - 1.0), nearEnd, nearDist);

    // Each sheet cuts the water (and land) in a hard straight line. Thin the sheets right above the surface and
    // bring them back over WATER_FADE_HEIGHT blocks, with a ragged edge, so the cut turns into a soft gradient.
    float aboveSea = wallPos.y - SEA_LEVEL + (broad - 0.5) * 14.0;
    float waterFade = mix(WATER_MIN_ALPHA, 1.0, smoothstep(0.0, WATER_FADE_HEIGHT, aboveSea));

    float alpha = min(vertexColor.a * density, 1.0) * vertical * distanceFade * nearFade * grazingFade * waterFade;
    if (alpha < 0.002) {
        discard;
    }
    fragColor = vec4(clamp(vertexColor.rgb * shade, 0.0, 1.0), alpha) * ColorModulator;
}
