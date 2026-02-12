# Gradle Scripts

This directory contains utility Gradle scripts for the project, organized to keep the root `build.gradle.kts` clean.

## Scripts

### `dot-dependency-graph.gradle.kts`
Generates a DOT file representing the project's internal module dependency graph.
- **Task**: `generateDotGraph`
- **Features**:
    - **Module Highlighting**: Automatically detects and highlights application entry points (e.g., `:composeApp`) in green.
    - **Third-Party Tracking**: Can be configured to track and display specific external libraries (e.g., Ktor, Serialization) to see how they are used across modules.
    - **Visual Customization**: Nodes and edges are styled for clarity (Helvetica font, color-coded nodes).
- **Usage**: Output can be pasted into any DOT viewer or Graphviz tool.

### `mermaid-dependency-graph.gradle.kts`
Generates a Mermaid diagram representing the project's dependency graph.
- **Task**: `generateMermaidGraph`
- **Usage**: Ideal for embedding directly into GitHub Markdown files or other documentation tools that support Mermaid.

### `appimage-package.gradle.kts`
Provides tasks to package the Compose Multiplatform application as a standalone Linux AppImage.
- **Prerequisites**: Must be applied to a module using the Compose Multiplatform plugin.
- **Tooling**: Automatically downloads and caches `appimagetool` in the `.gradle/tools` directory.
- **Tasks**:
    - `packageAppImage`: Generates an optimized AppImage using the ProGuard-processed Release UberJar. Use this for production-like testing.
    - `packageAppImageNoProguard`: Generates an AppImage using the full UberJar (skipping ProGuard). This is useful for debugging runtime issues that might be caused by code stripping or obfuscation.
- **Output**: Located in `build/appimage/output/`.