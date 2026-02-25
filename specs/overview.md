# Sky Map Overview

## Purpose

Sky Map is an Android planetarium application that transforms your phone into a window on the night sky. By using device sensors (accelerometer, magnetometer, gyroscope), the app determines where you're pointing and displays the corresponding celestial objects in real-time.

## Core Functionality

### Real-Time Sky Visualization
- Display stars, planets, constellations, and deep-sky objects
- Synchronized with device orientation via sensor fusion
- Accurate positions based on user location and current time

### Sensor-Based Pointing
- Point your device at the sky to see what's there
- Uses modern Android rotation sensor (fused accelerometer + magnetometer + gyroscope)
- Falls back to legacy sensor processing when rotation sensor unavailable

### Celestial Object Identification
- Tap objects to see names and information
- Search for specific objects by name
- Auto-center and zoom to searched objects

## Key Features

| Feature | Description |
|---------|-------------|
| **Real-time Display** | Sky updates as you move your device |
| **12 Display Layers** | Stars, planets, constellations, grids, ISS, meteor showers, etc. |
| **Search** | Find celestial objects by name with autocomplete |
| **Time Travel** | View the sky at any date/time (past or future) |
| **Manual Control** | Drag to navigate when sensors unavailable |
| **Night Mode** | Red-tinted display to preserve night vision |
| **Image Gallery** | Browse astronomical photographs |
| **ISS Tracking** | Real-time International Space Station position |

## Target Audience

- Amateur astronomers identifying celestial objects
- Stargazers learning constellations
- Educators teaching astronomy
- Anyone curious about the night sky

## Platform Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Target SDK 35
- Device sensors: accelerometer, magnetometer (gyroscope optional but recommended)
- GPS or network location (optional, can use manual coordinates)

## Build Flavors

| Flavor | Description | Google Services |
|--------|-------------|-----------------|
| **gms** | Google Play Store version | Analytics, Location |
| **fdroid** | F-Droid open source version | None |

## History

- **2009**: Originally released by Google as "Google Sky Map"
- **2012**: Open-sourced and transferred to community
- **Present**: Actively maintained on GitHub
