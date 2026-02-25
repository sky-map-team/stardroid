# Architecture Overview

Sky Map is an Android planetarium app that displays the night sky in real-time using device sensors and OpenGL rendering. The codebase is written in Java and Kotlin, targeting Android SDK 26–35.

> **Detailed architecture documentation lives in [`specs/architecture/`](../specs/architecture/README.md).** This file provides a high-level summary and diagram; follow the links below for specifics.

## High-Level Architecture

```mermaid
graph TB
    subgraph Android["Android Application (app/)"]
        UI["Activities & UI"]
        DI["Dagger DI"]
        Sensors["Sensor Pipeline"]
        Control["AstronomerModel"]
        Layers["Layers (12)"]
        Renderer["OpenGL Renderer"]
    end

    subgraph Data["Data Layer"]
        Proto["Binary Protobuf Assets"]
        DataModel["datamodel/ (Proto Definitions)"]
    end

    subgraph Tools["Build Tools (tools/)"]
        Catalogs["Star Catalogs"]
        Generator["Data Generator"]
    end

    Sensors --> Control
    Control --> Renderer
    Layers --> Renderer
    Proto --> Layers
    DataModel --> Proto
    Catalogs --> Generator
    Generator --> Proto
    UI --> DI
    DI --> Control
    DI --> Layers
```

## Module Structure

| Module | Purpose |
|--------|---------|
| **app/** | Main Android application — activities, rendering, sensor handling, layers |
| **datamodel/** | Protocol buffer definitions for astronomical objects (`source.proto`) |
| **tools/** | Standalone Java utilities for converting star catalogs to binary protobuf format |

See [Module Structure](../specs/architecture/module-structure.md) for package-level detail.

## Build Flavors

- **gms** — Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties` for release builds.
- **fdroid** — Pure open source, no Google dependencies.

## Key Topics

| Topic | Documentation |
|-------|--------------|
| Dependency Injection (Dagger 2) | [specs/architecture/dependency-injection.md](../specs/architecture/dependency-injection.md) |
| Rendering Pipeline | [specs/rendering/README.md](../specs/rendering/README.md) |
| Coordinate Transformation | [specs/architecture/README.md](../specs/architecture/README.md#coordinate-transformation) |
| Data Flow | [specs/architecture/data-flow.md](../specs/architecture/data-flow.md) |
| Sensor Orientation | [specs/sensors/orientation.md](../specs/sensors/orientation.md) |

## Further Reading

- [specs/architecture/](../specs/architecture/README.md) — Full architecture documentation
- [Design Documents Index](design/README.md) — Older design docs on sensors, rendering, ephemeris, and more
- [CLAUDE.md](../CLAUDE.md) — AI-oriented codebase guide with build commands and testing notes
