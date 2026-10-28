#version 300 es
precision highp float;
in float photonEnergy;
out vec4 color;
void main() { color=vec4(photonEnergy,0,0,1); }
