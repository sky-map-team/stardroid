# Rethinking How We Generate Data Files

## Intro

Currently Sky Map's data is bundled as binary protocol buffer files containing stars,
constellation lines, messier objects etc. These binary files are generated from text files,
typically csv or text proto files, during a separate step in the build process. This causes two
problems:
   1. The data files are static. There is no opportunity to update them online when something
    interesting happens (e.g. Comet Neowise). Not can users choose to just have the basics
    with a light footprint or expand to more objects if they wish.
   1. The build process is awful, largely due to the way localization is done. Typically in
    Android strings are stored in `strings.xml` files which are processed during the build to
    have unique integer ids. Localization is done by supplying multiple copies of `strings.xml`,
    one for each language. The binary datafiles include these integer ids so that the code
    can look up the correct translation for the name of an object at runtime. The problem is that
    these ids are generated and are not known in advance. Consequently Sky Map uses a convoluted
    3-phase build process where the app is built, the ids are generated, the data files are then
    generated against these ids (using some awful string hackery) and then finally the app is
    rebuilt with the datafiles. Get this wrong, and the labels will all be in the wrong place
    or the app will simply crash. We've had several releases where stars have been inadvertently
    labelled as "Terms of Service" or "Gyroscope". It makes the build process flaky and not
    especially portable.  __FIXED__

## Requirements

   * A human-friendly 'base format' in which to store the canonical versions of the data
   * Localization must work, and must be relatively friendly for our volunteer translators
   * Wire format should be compact (in case I end up paying for bandwidth)
   * App storage format should be compact (not everyone has lots of storage)
   * Allows for a 'standard' build process instead of the monstrosity we have now. It's OK to have
   a separate processing stage to create the datafiles that need to ship with the app, but it
   shouldn't depend on the app itself as it does now.  __FIXED__

TBD - currently there's a bit of a confused mess between "astronomical sources" - ie the actual
objects themselves and UI elements like line and point sources. This also needs work.

## What needs to go in the data model?

In the following P0 gives functional parity (note we might actually currently have more in the
app but not used them.)

### Fixed, point like objects such as stars
Note that some future version could also show the stars from different locations (e.g. from Alpha Centuri) 
or different times (one millions years hence). Steady on though.

#### P0 (parity)

   * Ra
   * Dec
   * Apparent Magnitude
   * Scientific name
   * Common name
   * Layer name (or some other means to group them)

#### P1

   * Color
   * Representative image
   * Link to wikipedia?

#### P2

   * Absolute magnitude
   * Constellation it belongs to
   * Distance
   * Informational snippet (scraped from wikipedia if T&C allow)
   * Other informational data - see the [wikipedia side bar](https://en.wikipedia.org/wiki/Polaris)
   * What about variable stars?
 

### Fixed, extended objects such as Nebulae and galaxies
Currently these are modelled as points.

#### P0 (parity)

   * Ra
   * Dec
   * Apparent Magnitude
   * Scientific name
   * Common name

#### P1

   * Type (e.g. galaxy, globular cluster)
   * Map image with some bounds (ra, dec for the corners? or just a size?)
   * Representative image (for info page)
   * Link to wikipedia?

#### P2

   * Absolute magnitude
   * Constellation it belongs to
   * Distance
   * Informational snippet (scraped from wikipedia if T&C allow)
   * Other informational data - see the [wikipedia side bar](https://en.wikipedia.org/wiki/Andromeda_Galaxy)

#### Larger extended objects
Images that could cover more of the sky such as constellation drawings, hubble imagery or
infrared surveys.

   * As above, but probably more important to have precise image bounds and maybe some info
     on how to transform.

### Fixed, occasionally visible point like objects: meteor radiants
Always in the same place, but not always relevant.

   * Mostly as for stars, but with a visibility constraint. Perhaps simple like a time bound.

### Fixed, line-based features: constellation lines, boundaries, right ascension/declination grids, Ecliptic

Some things, like the grids and Ecliptic might be best just calculated as they are now.

#### P0 (parity)

   * Scientific name
   * Common name
   * Name location (Ra, dec)
   * Search location 
   * Sequence of polylines: Color, [ra, dec] segments
    
### User-specific line-based features: zenith, nadir, horizon, "real horizon" (generated from location)

Currently calculated.

#### P0 (parity)

   * Name
   * Name location
   * Sequence of polylines: Color, [lat, long] segments

#### P1

   * Search location

### Other User-specific features: daylight shading, cloud coverage, adjustments for light pollution.

TBD

### Time-dependent sun-orbiting objects: sun (special case!), planets, comets
Mostly don't move that quickly.

#### P0 (Parity)

   * Scientific name
   * Common name
   * Map Image and map scaling (different from actual scaling)
   * Distance from Earth (or some way to calculate depth order)
   * Update frequency
        * Used in early days to avoid frequent recalculation - probably not a problem on modern
        devices.
   * Orbital elements 
    
#### P1

   * Object metadata, as for stars
   * Constants to calculate apparent magnitude from phase angle (not used currently as we
    just show the image).
   * A scaling factor so we can show the map images correctly sized.


### Time-dependent earth-orbiting objects: Moon, ISS, Easter eggs like Santa, StarOfB
Can move quickly (ISS). User's location on Earth can matter a little (Moon) or be crucial (ISS).
 
#### P0 (Parity)

   * Scientific name
   * Common name
   * Image sequence (moon phases)
   * Geocentric orbital elements
    
#### P1

   * Object metadata, as for stars

## Ideas
### Localization

This is a tricky one as a search of the entire web (I read it all) yielded no great ideas on
how to elegantly localize 'dynamic' data such as you might have if our data files were stored
in a database.

Should we keep all the localizations in with the object data? Or can we continue to leverage
Android's resource tooling?

One example would be to have the base data files store the English (default - sorry!) names.
A script could extract them to a strings.xml file so we can take advantage of Android's tool chain
to spot missing translations.
 
This might work for the base
data, but we'll need some other mechanism for downloaded data (won't be in the values folder)
and large data like object descriptions. This can either be in the data files or we can
accept that downloaded data will either not be localized or only when the app is updated. This
might not be so bad for the more obscure objects that don't have internationalizable names.

### Storing the data on device

Using a database as provided by [Room](https://developer.android.com/training/data-storage/room)
looks promising:

    + Can prepopulate the database from a provided file. 
    + Can store arbitrary objects

### Data format

Note: There are several parts to this problem with different weightings in the solution. We don't
need one size fits all.

   * The 'ground truth' format - mostly edited by humans. Currently we have several.
   * The wire format (for when we do OTA updates). Needs to be compact and extensible - backwards
    compatibility could be an issue here.
   * The on-device format. Also needs to be compact. Not so important to be backwards compatible
    since we can transform it on update, or simply redownload the data.

#### Protocol Buffers (binary)
    + Compact format for storage and transmission.
    - Not human readable, so other base format needed
    - Needs a fairly non-standard tool chain (how well used are they outside of Google?)
    + Android [support](https://github.com/protocolbuffers/protobuf/blob/v3.9.0/java/lite.md)
    + gRPC [support](https://developer.android.com/guide/topics/connectivity/grpc)

#### Protocol Buffers (text)
    + Human-readable
    + Quite compact and nice to read
    + What we already use
    + Type safe
    - Poorly documented

Only needed as the 'reference' file - the binary files would be shipped with the app.

##### Example (made up schema)

```prototext
name: "Sirius"
ra: 123
dec: 10
localized_name { 
  locale: "fr"
  name: "sirius"
}
localized_name {
  locale: "de"
  name: "blah"
}
```

#### JSON
    + Good support.
    + Human-readable
    - Not as compact and nice to read as text protos?

#### Example

```json
{
   "name":"Sirius",
   "ra":110,
   "dec":10,
   "localized_names":{
      "fr":"sirius",
      "de":"blah"
   }
}

```

#### XML
    + "Human"-"readable"
    - Pretty darn ugly

##### Example
I can't even.

#### CSV
    + Human-readable
    + Easy to manage in, say, a spreadsheet
    + Good support
    - Not great for structured data

##### Example

```csv
name, ra, dec, localized_names
Sirius, 100, 10, fr:sirius|de:blah
```

Other options: https://en.wikipedia.org/wiki/Comparison_of_data-serialization_formats

### Online vs Offline

Key features to preserve in Sky Map are its small footprint and ability to work offline where
cell reception is limited. This suggests that
   * It should ship with a small dataset that will satisfy most people
   * There should be the means to augment that dataset over the network

Other desirable features would be
   * The ability to queue updates for download later (that would require some downloadable index)
   * The ability to push new updates to phones (e.g. if a dataset contains errors)
   * The ability to make users aware of new updates
   * Low bandwidth requirements (both for the users' and my own bank balances)
   

## Plan
  1. Try setting the resource ids on start up, instead of with the current baroque system. __DONE__
  1. Refactor the planets, sun and moon layers to make them less complex (separate design)
  1. Add support in code for ISS (it's a much needed feature)
  1. Move the fixed point items (stars, messier) to a new system
  1. Move the fixed constellation lines to a new system
  1. Move the planets etc to a new DB-based system 
