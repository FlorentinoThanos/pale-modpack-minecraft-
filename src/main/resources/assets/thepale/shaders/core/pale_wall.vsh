#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 PaleCameraOffset; // camera position relative to the wall centre (y is absolute)

out vec3 cameraRelPos;
out vec3 wallPos;
out vec2 wallCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    cameraRelPos = Position;
    wallPos = Position + PaleCameraOffset;
    wallCoord = UV0;
    vertexColor = Color;
}
