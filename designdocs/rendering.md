# Rendering
The sky is currently rendered as a set of points, lines, images and text using OpenGL code.

At present there are unused classes and interfaces in the code that should be removed and things
are badly named so there is confusion between the primitives that the renderer supports and the
classes representing astronomical objects (which are typically composites).

## Source classes
Each astronomical object is transformed into a collection of so-called "Sources" of different types.
The these primitive types are:
    * Points
    * Lines
    * Images
    * Text

These sources are in turn rendered using OpenGL by the renderer classes described below.

There are two main interfaces: `AstronomicalSource` provides methods that are not directly to do with rendering,
but rather are to facilitate search.
`Sources` provides methods to get the primitive types associated with the objects: lines, images,
etc.

The primitives are `LinePrimitive`, `PointPrimitive`, `TextPrimitive` and `ImagePrimitive` and there are a
few interfaces to separate out common functionality. For example, the primitives which have a specific
position and the sources which have a color.

## Concrete astronomical objects

Solar system objects are represented by the `PlanetSource` class which implements `AbstractAstronomicalSource`.
Stars and "messier" objects - that is the data loaded from the serialized proto files get converted into
`ProtobufAstronomicalSource`s, again, implementing `AbstratctAstronomicalSource`.

## Layers

The different astronomical objects are organized into "layers" which can be independently switched
on and off.  The Layers are registered with the renderer code, specifically the `RendererController`.
Some simple layers, such as the `HorizonLayer` contain their own simple subclass of `AbstractAstronomicalSource`
to represent the primitives they want to render (e.g. a line and a few labels in the case of the horizon.)

Data-based layers like the `MessierLayer` extend the `AbstractFileBasedLayer` which knows how to turn
the serialized protocol buffers into `ProtobufAstronomicalSource`s.

## Renderer classes

The API used by the layer/astronomical source code appears to be:
    * `RendererObjectManager.UpdateType` - indicates the type of UI update requested
    * `RendererController` - allows the queueing of changes to the UI and access to different
kinds of `Managers` (Point, Line etc).  Beware, there are XManagers and XObjectManagers because
of course there are.
