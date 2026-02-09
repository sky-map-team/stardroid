# Design Documents

Technical design documents for the Sky Map project.

| Document | Description | Status |
|----------|-------------|--------|
| [sensors.md](sensors.md) | Sensor calculation and coordinate transformation in `AstronomerModel` | Current |
| [sensor_dataflow.md](sensor_dataflow.md) | Complete data flow from device sensors through to rendered output | Current |
| [rendering.md](rendering.md) | Rendering pipeline: Layer → Source → Primitives → OpenGL | Current |
| [ephemeris.md](ephemeris.md) | Calculating Right Ascension and Declination for astronomical objects | Current |
| [datageneration.md](datageneration.md) | Data file generation pipeline and binary protobuf format | Current |
| [analytics.md](analytics.md) | Analytics implementation with Firebase | Current |
| [cometlayer.md](cometlayer.md) | Tracking temporary celestial events (comets) with date-based visibility | Stub |
| [iss.md](iss.md) | ISS tracking feature design and data sources | Stub |
| [panoramas.md](panoramas.md) | HeyWhatsThat panorama integration | Stub |
| [ux.md](ux.md) | UX feature ideas and brainstorming | Stub |

**Status key:**
- **Current** — Actively reflects the codebase
- **Stub** — Incomplete or exploratory notes
