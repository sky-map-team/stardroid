# Stardroid Blueprint - Target Architecture

This directory contains the **target architecture specification** for Stardroid, defining the modern, AR/Vulkan-capable future state of the application.

## Purpose

The blueprint answers:
- What should the app become? (AR-capable sky map with modern rendering)
- What are the stable domain abstractions? (astronomy, math, sensor fusion)
- How do we enable AR/Vulkan without rewriting everything?
- What's the migration path from the current codebase?

## Navigation

| Spec | Purpose | Status |
|------|---------|--------|
| [core-domain.md](core-domain.md) | Astronomical calculations, coordinate systems, sensor fusion - the stable intellectual core | TODO |
| [rendering-abstraction.md](rendering-abstraction.md) | Graphics abstraction layer - enables swapping OpenGL for Vulkan/AR | TODO |
| [ar-vulkan-target.md](ar-vulkan-target.md) | AR/Vulkan target architecture design | TODO |
| [migration-roadmap.md](migration-roadmap.md) | Step-by-step migration from current to target | TODO |

## Key Principles

### Domain-Driven Design
The **core domain** (astronomy, mathematics, sensor fusion) is the app's intellectual value. Everything else (rendering, UI, storage) are implementation details.

### Rendering Abstraction
Graphics are **pluggable backends**. The core domain doesn't care whether rendering is OpenGL, Vulkan, or ARCore - it speaks in terms of "draw a star at RA/Dec with magnitude X".

### Modern Android Standards
Target architecture uses current best practices:
- Material 3 for UI
- Jetpack Compose for declarative UI (future)
- Kotlin Coroutines/Flow for async
- Room/DataStore for persistence
- Hilt for dependency injection
- WorkManager for background tasks

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Material 3)                      │
│  Activities, Fragments, Bottom Sheets, Compose (future)      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              Presentation Layer (ViewModels)                 │
│         Screen state, user interactions, navigation           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Core)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Astronomy  │  │   Sensors    │  │    Search    │    │
│  │  (RA/Dec,     │  │ (Fusion,     │  │  (Prefix     │    │
│  │  Transform)  │  │  Orientation)│  │   Store)     │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              Rendering Abstraction Layer                   │
│         RendererInterface │ GraphicPrimitive │ Shader      │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  OpenGL Renderer│  │ Vulkan Renderer │  │   AR Renderer  │
│   (Current)     │  │    (Future)     │  │    (Future)    │
└────────────────┘  └────────────────┘  └────────────────┘
```

## Relationship to Current Specs

This blueprint **extends and refines** the existing architecture documentation:

- **[core/](../core/README.md)** - Will contain the domain layer specs from this blueprint
- **[rendering/](../rendering/README.md)** - Current OpenGL rendering → blueprint defines abstraction
- **[ui/](../ui/README.md)** - Current UI → blueprint defines Material 3 modernization
- **[features/](../features/README.md)** - Feature specs remain valid, implementation will evolve

## Migration Philosophy

**Evolutionary, not revolutionary:**
- Preserve the working core domain (it's mathematically correct)
- Abstract the rendering layer (make it pluggable)
- Modernize UI incrementally (Material 3 → Compose)
- Add AR as a new rendering mode, not a replacement
- Enable Vulkan as an optional backend, not a rewrite

## Document Conventions

Each blueprint spec:
- 200-300 lines maximum
- Defines **what** and **why**, not just **how**
- Includes: Purpose, APIs, Algorithms, Dependencies, Testing
- Cross-references related specs
- Provides implementation guidance
