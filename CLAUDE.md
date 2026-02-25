# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See [AGENTS.md](AGENTS.md) for full project context, architecture, build commands, and code style guidelines shared across all AI assistants.

## Claude-Specific Notes

### Build Environment

Always set the correct Java version before building:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=~/Library/Android/sdk
```

### Adding Dialog Fragments

Follow the pattern in `AbstractDynamicStarMapModule`:
1. Add a `@Provides @PerActivity` method returning `new XyzDialogFragment()`
2. Add `XyzDialogFragment.ActivityComponent` to `DynamicStarMapComponent` interface
3. Inject the fragment in `DynamicStarMapActivity` and handle in `onOptionsItemSelected`
