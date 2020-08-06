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
    especially portable.
   1. It's pretty bad, really.

## Ideas
### Localization

This is a tricky one as a search of the entire web (I read it all) yielded no great ideas on
how to elegantly localize 'dynamic' data such as you might have if our data files were stored
in a database.

### Storing the data

Using a database as provided by [Room](https://developer.android.com/training/data-storage/room)
looks promising:

    + Can prepopulate the database from a provided file. 
    + Can store arbitrary objects

### Data format

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
