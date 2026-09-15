# NOTICE

Sky Map v2

Copyright © 2026 Penterakt LLC and the Sky Map contributors.

This module is licensed under the GNU General Public License v3 or later (see
[`LICENSE.md`](LICENSE.md)). It carries a single file inherited from **Sky Map v1** under the
Apache License, Version 2.0, recorded here.

---

## Apache-2.0 material in v2

One file: the compass-calibration animation,
`app/src/main/assets/calibration/calib.gif`. It was added to Sky Map v1 in 2016 — after the
January 2012 Google open-source release — so it is **not Google-authored**. It is inherited under
v1's Apache-2.0 licence, and its authorship has not been established.

**No Google-authored material remains in v2.** The two time-travel sound effects, which shipped as
`.wav` in the 2012 release and were later transcoded, were the last of it; they were removed
entirely rather than carried forward.

Sky Map v1 ships no `NOTICE` file, so Apache-2.0 §4(d) is not engaged; this file exists to satisfy
§4(c) for the item above. Should `calib.gif` prove to be the project's own work, or be replaced,
this file can be deleted — nothing else in v2 requires it.

---

## What is *not* inherited

This section exists because an earlier draft of this file overstated the position, and an
overstated notice is not a neutral error: it would imply that substantial parts of v2 are
available under permissive terms when they are not.

**The source code is an independent reimplementation, not a port.** v2 was written to match v1's
*behaviour*, which is not protectable subject matter. Where the two are compared directly the
implementations diverge: `RaDec` moved from mutable `Float` to immutable `Double` with a different
API and corrects a v1 sign-handling bug in `decDegreesFromDms`; `TextureCache` caches eclipse and
phase geometry and bears no relation to v1's resource-ID-keyed `TextureManager`;
`LabelAtlasPacker` is an original design. No Google-authored source is redistributed.

**The translations are not Google's.** 29 locale resource sets were carried across from v1. Of the
strings that survive from the 2012 Google release, the remainder are isolated single words —
`Sonne`, `Mond`, `NORDEN`, `ZENIT` — which are dictionary terms rather than copyrightable
expression, and for which only one correct translation exists. Everything else post-dates 2012 and
is the work of Sky Map's community translators, credited in
`app/src/main/res/values/contributors.xml`. v1's string resources carry no copyright notice, so
§4(c) has none to retain.

---

## Third-party scientific data and imagery

Star catalogs, ephemeris data and celestial imagery are governed by their own terms, which are not
uniform: some are public domain (NASA, ESA, JPL, USNO), others are CC BY 4.0 (ESO, the IAU,
IAU/Sky & Telescope, the EHT Collaboration). CC BY 4.0 is not public domain and carries ongoing
attribution obligations. Full attribution is in the app's credits screen,
`app/src/main/res/values/credits.xml`, and in `app/src/main/assets/planets/SOURCES.md`.

Classification of every shipped asset is in [`ASSET-LICENSES.txt`](ASSET-LICENSES.txt).

---

## Contributions

Contributions are made under the Sky Map Individual Contributor License Agreement (`CLA.md` at the
repository root). Contributors retain copyright in their work.
