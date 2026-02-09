# Issue 1: Expand Info Card Coverage to All Labeled Objects

## Summary

Ensure every object that displays a label on the sky map also has an educational info card available when tapped.

## Current State

- **145 objects** have info cards today (`object_info.json`)
  - 48 constellations, 40 stars, 20 galaxies, 18 clusters, 10 nebulae, 7 planets, 1 moon, 1 dwarf planet
- Labels come from multiple sources:
  - Protobuf data files in `app/src/main/assets/` (stars, Messier objects, constellations)
  - Programmatic layers: `PlanetSource`, `IssLayer`, `CometsLayer`, `MeteorShowerLayer`
- **Missing categories**: ISS, comets, meteor showers, and potentially some labeled stars/Messier objects not yet covered

## Acceptance Criteria

- [ ] Every labeled object on the sky map has a corresponding entry in `object_info.json`
- [ ] Every entry has: localized name, description (1-2 sentences), fun fact, object type
- [ ] Scientific data fields (distance, size, mass, spectral class, magnitude) populated where applicable
- [ ] Translations provided for main supported languages (pt, pt-BR, hi, th, sv, hu, id, da, ca)
- [ ] All existing unit tests pass
- [ ] New test verifies total object count matches labeled object count

## Technical Approach

### Step 1: Audit - Identify the Gap

Build a mapping of all labeled objects across every layer:

**Data-driven layers** (protobuf files):
- Parse `stars.binary`, `constellations.binary`, `messier.binary` to extract label string IDs
- Cross-reference each label's string resource key against `object_info.json` keys

**Programmatic layers**:
- `PlanetSource` (`app/src/main/java/.../space/`) - Sun, Moon, Mercury-Pluto (all 10 covered)
- `IssLayer.kt` (`app/src/main/java/.../layers/IssLayer.kt`) - "Space Station" label -> **NOT covered**
- `CometsLayer.kt` (`app/src/main/java/.../layers/CometsLayer.kt`) - Comet Leonard -> **NOT covered**
- `MeteorShowerLayer.kt` (if exists) - meteor shower radiants -> **check coverage**

**Output**: checklist of missing object IDs

### Step 2: Generate Missing Content

For each missing object, create:
```json
{
  "nameKey": "<string_resource_key>",
  "descriptionKey": "object_info_<id>_description",
  "funFactKey": "object_info_<id>_funfact",
  "type": "<planet|star|constellation|nebula|galaxy|cluster|other>",
  "distanceKey": "object_info_<id>_distance",
  "sizeKey": "object_info_<id>_size",
  "massKey": "object_info_<id>_mass",
  "spectralClass": "<if applicable>",
  "magnitude": "<if applicable>"
}
```

Follow existing tone and length:
- Descriptions: 1-2 factual sentences
- Fun facts: 1 engaging sentence, conversational
- Scientific data: concise with units

### Step 3: Add to Data Files

| File | Action |
|------|--------|
| `app/src/main/assets/object_info.json` | Add new entries |
| `app/src/main/res/values/celestial_info_cards.xml` | Add English strings |
| `app/src/main/res/values-pt/celestial_info_cards.xml` | Portuguese translations |
| `app/src/main/res/values-pt-rBR/celestial_info_cards.xml` | Brazilian Portuguese translations |
| `app/src/main/res/values-hi/celestial_info_cards.xml` | Hindi translations |
| `app/src/main/res/values-th/celestial_info_cards.xml` | Thai translations |
| `app/src/main/res/values-sv/celestial_info_cards.xml` | Swedish translations |
| `app/src/main/res/values-hu/celestial_info_cards.xml` | Hungarian translations |
| `app/src/main/res/values-id/celestial_info_cards.xml` | Indonesian translations |
| `app/src/main/res/values-da/celestial_info_cards.xml` | Danish translations |
| `app/src/main/res/values-ca/celestial_info_cards.xml` | Catalan translations |

### Step 4: Update Tests

- `ObjectInfoRegistryTest.kt` - Update expected object count
- Add test verifying that the `ObjectInfoRegistry` can resolve all new entries
- Verify `CelestialHitTester` can match new objects

## Key Files

| File | Role |
|------|------|
| `app/src/main/assets/object_info.json` | Object data registry (JSON) |
| `app/src/main/res/values/celestial_info_cards.xml` | English string resources |
| `app/src/main/java/.../education/ObjectInfoRegistry.kt` | Loads and provides object info |
| `app/src/main/java/.../education/CelestialHitTester.kt` | Hit-tests tapped objects |
| `app/src/test/java/.../education/ObjectInfoRegistryTest.kt` | Unit tests |

## Verification

```bash
# Run unit tests
./gradlew testGmsDebugUnitTest

# Build to catch resource errors
./gradlew assembleGmsDebug
```

Manual testing:
- Open app in manual mode
- Tap every labeled object category (star, constellation, planet, Messier object, ISS)
- Confirm info card appears for each
- Verify content is accurate and well-formatted

## Estimate

- Audit: small (scripting)
- Content generation: medium (bulk generation with Claude, review for accuracy)
- Translations: medium (9 languages)
- Testing: small

## Dependencies

None - this is an independent content task.
