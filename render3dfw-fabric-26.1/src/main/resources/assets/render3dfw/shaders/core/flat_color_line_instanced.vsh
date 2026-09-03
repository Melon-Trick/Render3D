#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform samplerBuffer Instances;

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

ivec4 instance_bytes(int texel) {
    return ivec4(round(texelFetch(Instances, gl_InstanceID * 3 + texel) * 255.0));
}

int unsigned_short(int lowByte, int highByte) {
    return lowByte | (highByte << 8);
}

int signed_short(int lowByte, int highByte) {
    int value = unsigned_short(lowByte, highByte);
    return value >= 32768 ? value - 65536 : value;
}

void main() {
    ivec4 first = instance_bytes(0);
    ivec4 second = instance_bytes(1);
    ivec4 third = instance_bytes(2);
    vec3 translation = vec3(
        signed_short(first.r, first.g),
        signed_short(first.b, first.a),
        signed_short(second.r, second.g)
    ) / 32.0;
    float instanceLineWidth = float(unsigned_short(third.b, third.a)) / 16.0;
    vec3 position = Position + translation;

    vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(position, 1.0);
    vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(position + Normal, 1.0);
    vec3 ndc1 = linePosStart.xyz / linePosStart.w;
    vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;
    vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * ScreenSize);
    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * instanceLineWidth / ScreenSize;
    if (lineOffset.x < 0.0) {
        lineOffset *= -1.0;
    }
    if (gl_VertexID % 2 == 0) {
        gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    } else {
        gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    }

    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
    vertexColor = vec4(second.b, second.a, third.r, third.g) / 255.0;
}
