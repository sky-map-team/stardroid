SPDX-License-Identifier: GPL-3.0-or-later

Copyright (c) 2026 Penterakt LLC.

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details: <https://www.gnu.org/licenses/gpl-3.0.html>

---

## Additional Permission under GNU GPL Version 3, Section 7

As an additional permission under section 7 of the GNU General Public License version 3, the
copyright holders of this Program grant you permission to convey the Program, or a work based on
the Program, through an application distribution service or platform — including without
limitation the Apple App Store, Google Play, and comparable services — notwithstanding that the
terms of service of such a platform impose restrictions on recipients' exercise of the rights
granted by this License, including without limitation:

  (a) restrictions on redistribution or further conveyance of the conveyed copy;
  (b) limits on the number of devices or accounts on which the conveyed copy may be installed
      or used;
  (c) a requirement that recipients accept the platform operator's terms of service; and
  (d) technical measures that control the installation or execution of the conveyed copy.

This additional permission applies solely to conveyance through such a platform. It does not
restrict any other right granted by the License, and it does not affect the availability of the
Corresponding Source, which remains available under the terms of the License for every version
conveyed.

As provided in section 7 of the License, you may remove this additional permission from any copy
of the Program, or from any part of it, that you convey.

**Why this exists.** The Free Software Foundation's position is that some application-store terms
impose conditions the GPL forbids adding, which is why GPL-licensed applications have previously
been withdrawn from such stores. This permission resolves that conflict so Sky Map can be
distributed through those channels while the source remains free software under GPLv3.

---

## Reserved Brand Assets — All Rights Reserved

The GPLv3 above covers all source code in this module **and** its functional resources —
strings, translations, themes, layouts, and functional UI artwork. Those form part of the
Corresponding Source and are free software.

A short, enumerated list of **brand identity assets** is reserved:

**Copyright © 2026 Penterakt LLC. All Rights Reserved.**

The authoritative list is the `[arr]` section of [`ASSET-LICENSES.txt`](ASSET-LICENSES.txt):
currently the launcher and notification icons, the welcome backdrop, and the sky-marker icon
set. Those files may not be reproduced, modified, or included in any fork or store
publication without express written consent from Penterakt LLC. Any independent distribution
must use a distinct name, icon, and visual identity.

**Nothing else in this module is reserved.** In particular, none of the following is
Penterakt property, and none is covered by the notice above:

| Category | Terms |
|---|---|
| Functional UI artwork (`[gpl]`) | GPLv3, with the source |
| Strings, translations, themes, layouts | GPLv3 — includes work by ~40 community translators |
| Assets inherited from Sky Map v1 (`[apache-v1]`) | Apache-2.0; copyright varies by file — see [`NOTICE.md`](NOTICE.md) |
| Scientific imagery and catalog data (`[third-party]`) | Under its own terms; attributed in `res/values/credits.xml` |

Third-party scientific sources are a mix of public domain (NASA/ESA/JPL) and **CC BY 4.0**
(ESO, IAU, IAU/Sky &amp; Telescope, EHT). CC BY 4.0 is *not* public domain — it carries ongoing
attribution obligations that survive redistribution.

`tools/check_asset_licenses.py` runs in CI and fails the build if any shipped asset is not
classified in `ASSET-LICENSES.txt`, so the reserved list cannot silently drift out of date.

---

## Trademark Notice

"Sky Map", the Sky Map launcher icon, and associated logos are used by Penterakt LLC as
trademarks identifying this application. Penterakt LLC does not consent to their use in any
independent fork, clone, or marketplace distribution in a manner likely to cause confusion as
to the origin of the software.

Nothing in this notice restricts descriptive use of the ordinary words "sky map", nor any
right granted by the GPLv3 above.

---

## Contributions

Contributions to this project are accepted under the Sky Map Individual Contributor License
Agreement (`CLA.md` at the repository root), which grants Penterakt LLC the rights needed to keep
offering the additional permission above. Acceptance is recorded automatically on your first pull
request.
