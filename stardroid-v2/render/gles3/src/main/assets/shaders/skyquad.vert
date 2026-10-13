// A world-anchored textured quad: planet discs, deep-sky photographs.
//
// GLES1 rebuilds all four corners on the CPU every frame, because their size depends on the
// field of view and a producer resubmitting once a minute cannot follow a pinch. Here the FOV is
// a uniform and the corners are arithmetic, so the CPU does nothing per frame and there is no
// vertex buffer: the quad comes from gl_VertexID.
uniform mat4 uViewProj;
uniform vec3 uCenter;
// u and v are the quad's half-axes in world units, already rotated by the image's position
// angle and scaled by the floored drawn size. They are built on the CPU from the same
// SizeFloor the Compose search arrow uses, so the two can never disagree.
uniform vec3 uHalfU;
uniform vec3 uHalfV;

out vec2 vUv;
out vec2 vDisc;

void main() {
    vec2 corner = unitQuadCorner(gl_VertexID);
    // -1..1 across the quad in each axis.
    vec2 signedCorner = corner * 2.0 - 1.0;
    vec3 world = uCenter + uHalfU * signedCorner.x + uHalfV * signedCorner.y;
    gl_Position = uViewProj * vec4(world, 1.0);
    // Texture v runs down the image while the quad's +v axis runs up it, matching the
    // (0,1)/(0,0)/(1,1)/(1,0) texcoords GLES1 writes per corner.
    vUv = vec2(corner.x, 1.0 - corner.y);
    // The unit-disc frame PhaseGeometry and EclipseGeometry both work in.
    vDisc = signedCorner;
}
