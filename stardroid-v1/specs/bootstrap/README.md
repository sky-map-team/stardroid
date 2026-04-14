# Bootstrap Specifications

This section contains specifications for bootstrapping the project from zero to a working Vulkan demo.

## Contents

| Specification | Description | Status |
|--------------|-------------|--------|
| [vulkan-demo.md](vulkan-demo.md) | Minimal Vulkan triangle demo on Android | Draft |

## Purpose

The bootstrap specs define the minimal foundation needed to:
1. Create a working Android project with modern tooling
2. Initialize Vulkan on an Android device
3. Render a simple triangle (proof of concept)

This foundation unblocks all other development work in the migration roadmap.

## Dependencies

Bootstrap is Phase 0 - it has no dependencies on other specs but unblocks:
- Phase 1: Rendering Abstraction Layer
- Phase 3: Vulkan Backend (full star rendering)
