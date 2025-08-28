# demo-jvm: Local development and run guide

This app is configured to use the locally published Knit plugin and libraries for fast iteration.

## 1) Publish updated local artifacts

Publish the plugin and libraries to the local file repo at `build/artifactLocalPublish`.

Use the skip flag so `:demo-jvm` doesn't try to resolve the plugin during publishing:

```bash
./gradlew -PskipDemoJvm=true \
  :knit-asm:publishAllPublicationsToProjectLocalRepository \
  :knit:publishAllPublicationsToProjectLocalRepository \
  :knit-plugin:publishAllPublicationsToProjectLocalRepository
```

Notes:

- The local dev version is typically `0.1.5-local` (defined in `gradle.properties` and used in the plugin DSL). Optionally bump to `0.1.5-local.N` to force a fresh resolve.
- Artifacts appear under `build/artifactLocalPublish/io/github/tiktok/...`.

## 2) Verify the local plugin is used

Run any Gradle task for `demo-jvm` (e.g., `help`) and look for the origin log:

```bash
./gradlew :demo-jvm:help
```

You should see a line similar to:

```
KNIT_PLUGIN_FROM=.../knit-plugin-0.1.5-local.jar
```

Seeing a `-local` version (or `-local.*`) and a path under your project/Gradle caches confirms you're using the locally published plugin.

If you see a non-`-local` version, refresh dependencies or republish with a bumped `-local` version.

## 3) Build the shadow (fat) JAR

```bash
./gradlew :demo-jvm:clean :demo-jvm:shadowJarWithKnit
```

The runnable JAR will be created at:

- `demo-jvm/build/libs/demo-jvm-allWithKnit.jar`

## 4) Run it

```bash
java -jar demo-jvm/build/libs/demo-jvm-allWithKnit.jar
```

## Extras

- The plugin writes a dependency tree to:
  - `demo-jvm/build/knit.json`
- Troubleshooting:
  - Re-publish after code changes (Step 1).
  - Bump to `0.1.5-local.1`, `0.1.5-local.2`, etc., then re-publish.
  - Force refresh: `./gradlew --refresh-dependencies :demo-jvm:help`.
  - Ensure you used `-PskipDemoJvm=true` during publishing.
  - Verify artifacts exist under `build/artifactLocalPublish`.
