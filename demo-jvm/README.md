# demo-jvm: Knit demo (shadow fat jar)

This demo shows Knit bytecode injection on a plain JVM app. It builds a shadow (fat) JAR and runs it.

## Prerequisites

- JDK 11 or newer (tested with Temurin 21 on macOS)
- Gradle (wrapper included)

Check your Java:

```bash
java -version
```

Optionally set JAVA_HOME (zsh):

```bash
export JAVA_HOME=$(/usr/libexec/java_home)
```

## One-time setup (local plugin)

This repo applies the Knit Gradle plugin via the plugins DSL and resolves it from a local file repository.
Publish the plugin to the local repo once before building the demo:

```bash
./gradlew :knit-plugin:publishAllPublicationsToProjectLocalRepository -x test
```

Notes:

- No signing is required for the local file-based repository.
- If/when the plugin is available on Maven Central for the used version, you can skip this step.

## Build the fat jar with Knit

Build the original shadow jar and then transform it with Knit into a new fat jar:

```bash
./gradlew :demo-jvm:clean :demo-jvm:shadowJarWithKnit
```

Artifacts:

- Original shadow JAR: `demo-jvm/build/libs/demo-jvm-all.jar`
- Transformed shadow JAR: `demo-jvm/build/libs/demo-jvm-allWithKnit.jar`

## Run

Run the transformed fat jar:

```bash
java -jar demo-jvm/build/libs/demo-jvm-allWithKnit.jar
```

Expected output:

```
Hello Knit!
```

## Troubleshooting

- VerifyError about stack map frames: rebuild with `shadowJarWithKnit` after publishing the plugin locally.
- “Cannot use project dependencies in a script classpath definition”: don’t add `project(":knit-plugin")` in `buildscript`; this demo uses the plugins DSL and the local file repo configured in `settings.gradle.kts`.
- JDK mismatch: ensure `java -version` shows JDK 11+ (Temurin 21 works).
