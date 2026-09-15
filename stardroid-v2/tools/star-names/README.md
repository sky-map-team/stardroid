# star-names — one-off name-augmentation tool

`augment_names.py` merges star names from two public catalogs into
`source-data/names/en.csv` and `source-data/names/universal.csv`:

- **IAU proper names** (e.g. "Deneb") — added as the star's primary English name unless the
  star already has one (existing primaries always win; spelling conflicts are logged and the
  IAU form is added as a secondary name).
- **Bayer designations** — spelled-out English form ("Alpha Cygni") in `en.csv` and the
  compact universal form ("α Cyg") in `universal.csv`, both secondary (`is_primary=0`), so
  they are searchable but never used as map labels.
- **Flamsteed designations** ("50 Cyg") — secondary, `universal.csv`.

Like `tools/harvest-v1/`, this is a data-preparation script, not a build step: run it, review
the CSV diff and the match report it prints, and commit the result. Re-running is idempotent.

```
python3 augment_names.py          # from this directory; rewrites source-data/names/*.csv
```

## Inputs (`data/`)

| File | Source | License |
|---|---|---|
| `IAU-CSN.txt` | [IAU Catalog of Star Names](https://www.pas.rochester.edu/~emamajek/WGSN/IAU-CSN.txt), IAU Working Group on Star Names, 2022-04-04 edition | CC BY 4.0 |
| `bsc5_extract.txt` | Column extract of the [Yale Bright Star Catalogue, 5th ed.](http://tdc-www.harvard.edu/catalogs/bsc5.html) (`bsc5.dat`) | Public-domain catalog data |

The BSC5 extract keeps only the columns the script needs and was produced with:

```
curl -sL http://tdc-www.harvard.edu/catalogs/bsc5.dat.gz | gunzip | \
  cut -c1-14,26-31,76-90,103-107 > data/bsc5_extract.txt
```

(Extract columns, 1-indexed: 1-4 HR, 5-7 Flamsteed, 8-10 Bayer, 11 superscript,
12-14 constellation, 15-20 HD, 21-35 RA/Dec J2000, 36-40 Vmag.)

## Matching

`source-data/stars.csv` carries no HIP/HD identifiers, so BSC5 records are matched to catalog
stars positionally. See the module docstring in `augment_names.py` for the algorithm and the
empirical basis of the tolerances (the catalog's rounded coordinates leave a clean gap between
true matches and wrong neighbours). Reading the printed match report is part of any re-run:
ambiguous pairs get no designation, shared system designations go to the brightest component,
and primary-name conflicts always keep the existing name.
