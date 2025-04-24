[![TikTok](https://img.shields.io/badge/TikTok-Android-black.svg?style=flat-square&logo=tiktok)](https://kotlinlang.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-hotpink.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/tiktok/knit/test.yaml?branch=master&style=flat-square)
[![GitHub license](https://img.shields.io/github/license/tiktok/knit?style=flat-square)](https://github.com/tiktok/knit/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.tiktok.knit/knit-plugin?style=flat-square&color=coral)](https://central.sonatype.com/artifact/io.github.tiktok.knit/knit-plugin)
[![Gradle](https://img.shields.io/gradle-plugin-portal/v/io.github.tiktok.knit.plugin?style=flat-square&color=lightskyblue)](https://central.sonatype.com/artifact/io.github.tiktok.knit/knit-plugin)

![Knit Banner](docs/main_logo.svg)

# Knit

Knit is a purely static, compile-time safe DI framework that leverages Kotlin language features to provide zero-intermediary dependency injection with exceptional ease of use.

Knit means connecting (dependencies between code), which is what our framework does.

## Basic Usage

```kotlin
@Provides
class User(val name: String) // Producer

class UserService(
    @Provides val name: String // Producer which can provide `String`
) {
    val user: User by di // Consumer which need a `User`
}

fun main() {
    val userService = UserService("foo")
    val user = userService.user // User("foo")
}
```

There are 2 basic concepts in Knit:

- `@Provides`, producers should be marked with `@Provides`, it means this member can be used to provide something.
- `by di`, consumers should be marked with `by di`, marked properties should be injected by dependency injection.

In the previous case:

- `User` has been marked with `@Provides`, it means `User` provides its constructor as a dependency provider, and this
  provider needs a `String` provided to construct a `User`.
- `UserService` has a constructor which needs a `String` and it also provides this type inside `UserService`.
- `UserService.user` can be injected through provided `User` constructor and provided parameter `name`.
- For `UserService` call-site, the only thing needs to do is construct it like a normal constructor call, and access its
  member directly.

## Advance Usage 📚

Check [the Advance Usage](docs/README.md) document for more, we have a separate page to show the detailed usage and some principles

## Setup 📦

Knit supports all JVM applications, including Android applications, and here is the latest Knit version ↓

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.tiktok.knit.plugin?style=flat-square&color=lightskyblue)](https://central.sonatype.com/artifact/io.github.tiktok.knit/knit-plugin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.tiktok.knit/knit-plugin?style=flat-square&logo=apache-maven?color=coral)](https://central.sonatype.com/artifact/io.github.tiktok.knit/knit-plugin)

### Setup with Android Transform

1. Apply the Knit plugin in your app module.
    
    Through gradle plugin portal:
    ```groovy
    plugins {
        id 'com.android.application'
        id 'org.jetbrains.kotlin.android'
        // apply it after android & kotlin plugins
        id 'io.github.tiktok.knit.plugin' version "${latestKnitVersion}"
    }
    ```

    Through maven central:
    ```groovy
    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath("io.github.tiktok.knit:knit-plugin:$latestKnitVersion")
        }
    }

    // apply it after android & kotlin plugins
    apply(plugin: "io.github.tiktok.knit.plugin")
    ```

2. Add runtime dependencies to the module which wants to use Knit.

    ```groovy
    dependencies {
        implementation("io.github.tiktok.knit:knit-android:$latestKnitVersion")
    }
    ```

### Setup for other JVM applications

Knit is isolated from the Android transform process, you can apply Knit plugin for other JVM applications.

1. Add classpath to your project's buildscript `build.gradle`.
    
    ```groovy
    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath "io.github.tiktok.knit:knit-plugin:$latestKnitVersion"
        }
    }
    ```
2. Apply the following plugin in your application module.
    
    ```groovy
    apply(plugin: "io.github.tiktok.knit.plugin")
    ```
3. For runtime dependencies, depends on `knit` rather than `knit-android`.

    ```groovy
    dependencies {
        implementation("io.github.tiktok.knit:knit:$latestKnitVersion")
    }
    ```
After applying the Knit plugin, Knit will generate some tasks for your `jar` tasks which named with `WithKnit` suffix, for example:

- `jar` task will generate a `jarWithKnit` task.
- `shadowJar` task will generate a `shadowJarWithKnit` task.

We recommend you to use [shadowJar](https://github.com/GradleUp/shadow) to ensure all dependencies are included in the jar file, otherwise you may encounter some issues when Knit can't find the dependency providers.

We have a sample project to show how to use Knit with a shadow jar application, check [demo-jvm](demo-jvm) module for more details.

### Setup with ByteX

[ByteX](https://github.com/bytedance/bytex) is a bytecode transformation framework which can make all bytecode transformation plugin shares the same transform pipeline. With ByteX, Knit can runs incrementally, usually faster than run the whole Android transform process.

1. Make sure you have well configured [ByteX](https://github.com/bytedance/bytex) in your project.
2. Add classpath to your project's buildscript `build.gradle`.

    ```groovy
    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath "io.github.tiktok.knit:knit-bytex:$latestKnitVersion"
        }
    }
    ```

3. Apply the following plugin in your app module.

    ```groovy
    apply plugin: 'tiktok.knit.plugin'

    Knit {
        enable true
        enableInDebug true
    }
    ```

4. Add runtime dependencies to the module which wants to use Knit.
    ```groovy
    dependencies {
        implementation("tiktok.knit:knit-android:$latestKnitVersion")
    }
    ```

## License

```
Copyright 2025 TikTok Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
