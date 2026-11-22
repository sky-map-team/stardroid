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
- **`stardroid-v2/`** — the current rewrite (Kotlin only, GPLv3, minSdk 28 — Android 9 is
  best-effort only, not officially supported). See
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

**Release builds go through PRs too.** A release (Android beta/production or an iOS TestFlight
build) is made on a `release/<version>` branch in a free worktree: version bump, What's New,
translations and any fixes to them are committed there, the build is uploaded from that branch,
and a PR is opened. Tag the built commit (`v<version>` / `ios-v<version>-bN`) and ask for a merge
commit so the tag stays in master's history. Never push a release commit straight to `master`.
See the `skymap.release` skill.

## Git Worktrees

Seven reusable worktrees live under `.worktrees/` in the repo root, named for Greek letters:
`stardroid-alpha`, `-beta`, `-gamma`, `-delta`, `-epsilon`, `-zeta` and `-eta`
(`.worktrees/stardroid-<letter>`). The `new-worktree` skill claims the first idle one, or creates
a new slot when none is free, and copies the untracked setup files. Idle ones sit on a detached HEAD, so there
are no placeholder branches.

### Default to a worktree for new features

Unless told otherwise, always build a new feature (or fix) in a **free Greek-letter worktree**,
not in the main checkout. A worktree is free when it is on a detached HEAD with a clean tree
(check with `git worktree list` and `git -C .worktrees/<name> status`). Claim one as described
below and do all edits, builds and commits there. Only work in the main checkout when the user
explicitly asks for that, or for documentation-only changes.

### Worktree lifecycle

**1. Claim a worktree for a new task** — fetch latest master, then branch from it:
```bash
git fetch origin
git -C .worktrees/stardroid-alpha switch -c feature/my-task origin/master
```

**2. Reset a worktree when done** — detach it at master so the feature branch can be deleted:
```bash
git -C .worktrees/stardroid-alpha switch --detach origin/master
```
Run `/clean-git` to do this for every merged or abandoned worktree and delete local branches whose
remote was deleted. It shows a dry-run plan first and skips worktrees with uncommitted changes.

Each worktree already contains the v2 build-critical files excluded from version control:
`stardroid-v2/local.properties`, `stardroid-v2/app/no-checkin.properties`,
`stardroid-v2/fastlane/play-store-credentials.json`, `stardroid-v2/.env`, and the two Firebase
plists `stardroid-v2/ios/SkyMap/GoogleService-Info.plist` and `GoogleService-Info-Dev.plist`. A
worktree without the plists builds an iOS app with Firebase unconfigured: no analytics, and every
Remote Config flag reads its shipped default. v1 is deprecated, so worktrees don't need its
equivalents. The same list is in `.worktreeinclude`, which Claude Code uses for worktrees it
creates; keep the two in step.

Signing keystores are not per-worktree: `no-checkin.properties` points at them by absolute path
under `~/.android/`. If you add a new worktree, copy the files above from the main checkout
before building.

`.worktrees/` is listed in `.gitignore` so worktree directories are never accidentally committed.

## Build Flavors

Both modules share the same flavor scheme:

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties`
  for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor: use `assembleGmsDebug`, not `assembleDebug`. See the `/build` skill for
all build, test, deploy, and data-generation commands.

## Comments

Keep comments short and only where they add something the code can't say: the *why*, a
non-obvious constraint, a units/coordinate-frame convention, or a pointer to the design doc. Don't
narrate what the code does, restate a name or signature, or leave multi-paragraph KDoc/Javadoc on
self-explanatory members. If a comment is needed to explain confusing code, prefer renaming or
simplifying the code first. Delete comments that have gone stale.

## GitHub comments

In PR and issue comments, number list items as plain "1", "2", never "#1": GitHub auto-links `#N`
to issue N. Real issue/PR references are fine.

## Strings

Remember to properly escape any text added as Android resource strings (e.g. ' must be escaped
with a single backslash as \'). New strings should be in *US English* - translations to
other locales are handled by a separate pipeline after features are implemented.

For v2 (`stardroid-v2/`), that pipeline (the `tm` CLI, driven by `.tmconfig.toml`) is now
live — see [`stardroid-v2/AGENTS.md`](stardroid-v2/AGENTS.md#translations) for how and when
to run it. v1 is deprecated and its translations are not maintained.

**Ask before running `tm translate`.** It calls an LLM provider and rewrites every locale,
so it is the maintainer's decision. Flag that translations have gone stale and propose the
command; don't run it unprompted. The read-only `tm` commands (`languages`, `coverage`,
`validate`) need no permission.
