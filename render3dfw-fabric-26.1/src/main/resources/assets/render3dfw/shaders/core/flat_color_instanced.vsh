#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform samplerBuffer Instances;

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

ivec4 instance_bytes(int texel) {
    return ivec4(round(texelFetch(Instances, gl_InstanceID * 3 + texel) * 255.0));
}

int signed_short(int lowByte, int highByte) {
    int value = lowByte | (highByte << 8);
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

    gl_Position = ProjMat * ModelViewMat * vec4(Position + translation, 1.0);
    vertexColor = vec4(second.b, second.a, third.r, third.g) / 255.0;
}
