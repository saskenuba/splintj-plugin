# SplintJ

An IntelliJ IDEA plugin that integrates [Splint](https://github.com/NoahTheDuke/splint), a Clojure linter and code analyzer.

## Features

- Real-time linting for Clojure files (.clj, .cljs, .cljc)
- Editor annotations with severity indicators
- Quick-fix suggestions for common issues
- Inline rule disabling via comments
- File exclusions via .splint.edn configuration

## Installation

Build the plugin from source:

```bash
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`. Install it via IntelliJ's plugin manager (Install from disk).

## Usage

The plugin runs automatically on Clojure files. You can also trigger analysis manually:

- **Keyboard shortcut**: `Ctrl+Shift+L` to run Splint on the current file

### Quick Fixes

When Splint reports an issue, the following quick-fixes may be available:

- **Replace with suggestion**: Apply Splint's recommended fix
- **Disable rule for this form**: Add `#_{:splint/disable [rule]}` comment
- **Exclude file from rule**: Add file to `:excludes` in `.splint.edn`

## Configuration

Configure the plugin in Settings > Tools > Splint:

- **Executable path**: Path to Splint JAR or native binary
- **Timeout**: Maximum execution time
- **Additional arguments**: Extra CLI flags

## Requirements

- IntelliJ IDEA 2023.1+
- Splint CLI (JAR or native binary)
- Optional: Cursive plugin for enhanced Clojure support

## Development

```bash
# Build
./gradlew buildPlugin

# Run tests
./gradlew test

# Clean build
./gradlew clean buildPlugin
```

## License

See LICENSE file for details.
