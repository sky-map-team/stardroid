# Visual Design
Some notes on the visual design.

## Logo

![Sky Map logo](../../assets/skymap-logo-large.png)

## Colors

These are the main colors in the logo:

| Role | Color | Hex Code | Preview | Purpose |
|------|-------|----------|---------|---------|
| Deep Space | Navy Blue | `#0D1B2A` | ![#0D1B2A](https://placehold.co/60x20/0D1B2A/0D1B2A.png) | Primary background for web/non-night-mode UI. |
| Star Gold | Amber/Orange | `#FF9F1C` | ![#FF9F1C](https://placehold.co/60x20/FF9F1C/FF9F1C.png) | Primary brand color, logo accents, and CTAs. |
| Starlight | Off-White | `#E0E1DD` | ![#E0E1DD](https://placehold.co/60x20/E0E1DD/E0E1DD.png) | Primary text and secondary icon color. |
| Lens Blue | Sky Blue | `#7EC8E3` | ![#7EC8E3](https://placehold.co/60x20/7EC8E3/7EC8E3.png) | Magnifying glass lens / sky field in the logo. |
| Planet Green | Medium Green | `#4DB848` | ![#4DB848](https://placehold.co/60x20/4DB848/4DB848.png) | Green planet in the logo; secondary accent. |
| Planet Red | Orange-Red | `#E05C34` | ![#E05C34](https://placehold.co/60x20/E05C34/E05C34.png) | Red/orange planet in the logo; alert accent. |

## Sky Rendering Colors

Colors used in the OpenGL sky rendering layers, defined in `app/src/main/res/values/colors.xml`.

| Resource Name | Description | Hex (ARGB) | Preview | Used In |
|---|---|---|---|---|
| `horizon_line` | Horizon circle line | `#784A7C59` | ![#4A7C59](https://placehold.co/60x20/4A7C59/4A7C59.png) | `HorizonLayer` |
| `horizon_label` | Cardinal point labels (N/S/E/W/Zenith/Nadir) | `#78F5B056` | ![#F5B056](https://placehold.co/60x20/F5B056/F5B056.png) | `HorizonLayer` |
| `grid_line` | Celestial grid lines and ecliptic | `#14F8EFBC` | ![#F8EFBC](https://placehold.co/60x20/F8EFBC/F8EFBC.png) | `GridLayer`, `EclipticLayer` |
| `sky_label` | Planet, comet, and meteor shower labels | `#FFF67E81` | ![#F67E81](https://placehold.co/60x20/F67E81/F67E81.png) | `SolarSystemRenderable`, `CometsLayer`, `MeteorShowerLayer` |
| `planet_body` | Planet dot (fallback when no image) | `#14817EF6` | ![#817EF6](https://placehold.co/60x20/817EF6/817EF6.png) | `SolarSystemRenderable` |
