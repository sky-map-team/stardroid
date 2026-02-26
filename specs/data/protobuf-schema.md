# Protocol Buffers Schema

Sky Map serializes all bundled astronomical catalog data using **Protocol Buffers (proto2)**. The schema is defined in `datamodel/src/main/proto/source.proto`; generated Java classes live in the `com.google.android.stardroid.source.proto` package.

## Schema (`source.proto`)

```proto
syntax = "proto2";
package stardroid_source;
option java_package = "com.google.android.stardroid.source.proto";
option java_outer_classname = "SourceProto";

enum Shape {
  CIRCLE = 0;
  STAR = 1;
  OPEN_CLUSTER = 2;
  GLOBULAR_CLUSTER = 3;
  DIFFUSE_NEBULA = 4;
  PLANETARY_NEBULA = 5;
  SUPERNOVA_REMNANT = 6;
  GALAXY = 7;
  OTHER = 8;
}

message GeocentricCoordinatesProto {
  optional float right_ascension = 1;  // degrees (0–360)
  optional float declination = 2;      // degrees (-90 to +90)
}

message PointElementProto {
  optional GeocentricCoordinatesProto location = 1;
  optional uint32 color = 2 [default = 0xFFFFFFFF];
  optional int32 size = 3 [default = 3];
  optional Shape shape = 4 [default = CIRCLE];
}

message LabelElementProto {
  optional GeocentricCoordinatesProto location = 1;
  optional uint32 color = 2 [default = 0xFFFFFFFF];
  optional int32 strings_int_id = 3;   // legacy integer resource ID
  optional string strings_str_id = 6;  // current: Android string resource name
  optional int32 font_size = 4 [default = 15];
  optional float offset = 5 [default = 0.02];
}

message LineElementProto {
  optional uint32 color = 1 [default = 0xFFFFFFFF];
  optional float line_width = 2 [default = 1.5];
  repeated GeocentricCoordinatesProto vertex = 3;
}

message AstronomicalSourceProto {
  repeated uint32 name_int_ids = 1;    // legacy integer resource IDs
  repeated string name_str_ids = 8;    // current: Android string resource names
  optional GeocentricCoordinatesProto search_location = 2;
  optional float search_level = 3 [default = 0.0];
  optional float level = 4 [default = 0.0];
  repeated PointElementProto point = 5;
  repeated LabelElementProto label = 6;
  repeated LineElementProto line = 7;
}

message AstronomicalSourcesProto {
  repeated AstronomicalSourceProto source = 1;
}
```

## Data Generation Pipeline

```
Raw catalogs (CSV / hand-authored ASCII)
       │
       ▼
tools/generate.sh
  StellarAsciiProtoWriter   → tools/data/stars.ascii
  MessierAsciiProtoWriter   → tools/data/messier.ascii
  (constellations.ascii is hand-authored, not generated)
       │
       ▼
tools/binary.sh
  AsciiToBinaryProtoWriter  → app/src/main/assets/*.binary
```

The intermediate `.ascii` files are proto text-format representations that can be read and edited by humans. The `.binary` files are standard serialized proto2 wire-format binaries.

## Runtime Loading

Binary assets are loaded in `AbstractFileBasedLayer`:

```kotlin
val bytes = context.assets.open(assetFilename).readBytes()
val sources = AstronomicalSourcesProto.parseFrom(bytes)
for (i in 0 until sources.sourceCount) {
    addRenderable(ProtobufAstronomicalSource(sources.getSource(i)))
}
```

## Key Files

| File | Purpose |
|------|---------|
| `datamodel/src/main/proto/source.proto` | Schema definition |
| `tools/data/stars.ascii` | Generated star proto text (from `stardata_names.txt`) |
| `tools/data/messier.ascii` | Generated Messier proto text (from `messier.csv`) |
| `tools/data/constellations.ascii` | Hand-authored constellation proto text |
| `app/src/main/assets/stars.binary` | Compiled star catalog |
| `app/src/main/assets/messier.binary` | Compiled Messier/special-objects catalog |
| `app/src/main/assets/constellations.binary` | Compiled constellation catalog |
