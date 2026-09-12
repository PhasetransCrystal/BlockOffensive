#version 150

in vec4 Position;

out vec2 texCoord;

uniform mat4 ProjMat;
uniform vec2 OutSize;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
