# AGENTS.md

This file provides project context for AI coding assistants working in this repository.

## Project Overview

Sky Map is an open-source Android planetarium app that displays the night sky in real-time using
device sensors and OpenGL rendering. Originally "Google Sky Map" (open-sourced 2011), now
community-maintained. The internal codename "Stardroid" remains in package names.

This repository holds two applications:

- **`stardroid-v1/`** — the legacy app (Java, Apache-2.0, Android SDK 26–36). See
  [`stardroid-v1/AGENTS.md`](stardroid-v1/AGENTS.md) for its architecture, code style, and
  testing conventions.
- **`stardroid-v2/`** — the current rewrite (Kotlin only, GPLv3, minSdk 29). See
  [`stardroid-v2/AGENTS.md`](stardroid-v2/AGENTS.md) for its architecture, code style,
  licensing, and testing conventions.

Most work happens inside one module or the other — check the relevant `AGENTS.md` there first.
This root file covers only what's shared across both.

github: https://github.com/sky-map-team/stardroid

## Branching

Always make code changes on a feature branch, never directly on `master`. Create a branch before
starting any work:

```
git checkout -b feature/<short-description>
```

Documentation-only changes (`.md` files, troubleshooting guides, skills, etc.) may be committed
directly to `master` without a feature branch.

Only commit to `master` when explicitly instructed to do so.

## Git Worktrees

Five reusable worktrees live under `.worktrees/` in the repo root:

| Worktree | Path | Placeholder branch |
|---|---|---|
| stardroid-alpha | `.worktrees/stardroid-alpha` | `worktree/stardroid-alpha` |
| stardroid-beta | `.worktrees/stardroid-beta` | `worktree/stardroid-beta` |
| stardroid-gamma | `.worktrees/stardroid-gamma` | `worktree/stardroid-gamma` |
| stardroid-delta | `.worktrees/stardroid-delta` | `worktree/stardroid-delta` |
| stardroid-epsilon | `.worktrees/stardroid-epsilon` | `worktree/stardroid-epsilon` |

### Worktree lifecycle

**1. Claim a worktree for a new task** — fetch latest master, then branch from it:
```bash
git fetch origin
git -C .worktrees/stardroid-alpha checkout -b feature/my-task origin/master
```

**2. Reset a worktree when done** — return it to its placeholder branch so the feature branch
can be deleted:
```bash
git -C .worktrees/stardroid-alpha checkout worktree/stardroid-alpha
```
After resetting all affected worktrees, run `/clean-branches` to delete the merged feature
branches. That skill uses `git branch -d` (safe delete) and will skip any branch still checked
out in a worktree.

Each worktree already contains the build-critical files excluded from version control
(`stardroid-v1/local.properties`, `stardroid-v1/app/local.properties`,
`stardroid-v1/app/no-checkin.properties`, keystores, `stardroid-v1/fastlane/play-store-credentials.json`). If you add a new worktree, copy
these files from the main worktree (`stardroid-v1/app/`) before building.

`.worktrees/` is listed in `.gitignore` so worktree directories are never accidentally committed.

## Build Flavors

Both modules share the same flavor scheme:

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties`
  for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor: use `assembleGmsDebug`, not `assembleDebug`. See the `/build` skill for
all build, test, deploy, and data-generation commands.

## Strings

Remember to properly escape any text added as Android resource strings (e.g. ' must be escaped
with a single backslash as \'). New strings should be in *US English* - translations to
other locales are handled by a separate pipeline after features are implemented.

For v2 (`stardroid-v2/`), that pipeline (the `tm` CLI, driven by `.tmconfig.toml`) is now
live — see [`stardroid-v2/AGENTS.md`](stardroid-v2/AGENTS.md#translations) for how and when
to run it. v1 has its own longer-standing `tm` setup; see
[`stardroid-v1/AGENTS.md`](stardroid-v1/AGENTS.md) for its conventions.
