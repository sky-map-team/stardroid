# icons — sky-marker icon generator

`generate_icons.py` is the single source of truth for the ten sky-marker glyphs (eight
deep-sky types + two meteor radiants). It emits both the SVG design masters (`dso/`) and
the shipped 4×-density webps (`app/src/main/assets/catalog/icons/`) from one geometry
table, with the D62 sign-off parameters baked in: stroke 1.4 on a 24-unit grid, 20 dp draw
size (radiants 32 dp), 35 % dark halo.

```
python3 -m pip install Pillow   # needs webp support (standard wheels have it)
python3 generate_icons.py       # regenerates dso/*.svg and the assets; commit the result
```

Output is deterministic — re-running with unchanged geometry produces identical files.

Licensing: the script is GPLv3 like the rest of the repository's code; the icon artwork it
defines (geometry, SVG masters, webps) is All Rights Reserved, Penterakt LLC — see
`dso/LICENSE.md` for the masters, and the `[arr]` section of `ASSET-LICENSES.txt` for the
shipped webps.
