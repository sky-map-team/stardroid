# Comet Layer
To track temporary events like comets we want to have a new layer that

1 Only shows the objects between certain dates (like the meteor shower layer)
1 Can generate Ra, Dec from a table of positions.

## Requirements
1 Easy of use: astro data tends to be published as a table of dates and Ra, Dec in hms or dms.
1 Outside the date range the object should disappear
1 Flexible. The initial usecase is comets but that might change later.

## Quick design sketch
We'll have a new Layer for managing the objects (initially just comet leonard and maybe the
old comet from 2019). Rather than add a new enable/disable button we'll have it show along
with the planets or meteors layer.

