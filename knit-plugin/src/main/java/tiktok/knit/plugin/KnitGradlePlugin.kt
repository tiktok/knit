// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * Created by yuejunyu on 2025/4/15
 * @author yuejunyu.0
 */
abstract class KnitGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target.tryConfigAndroid()) return
        if (target.tryConfigJvm()) return
        System.err.println("cannot found any knit target available.")
    }

    private fun Project.tryConfigAndroid(): Boolean {
        plugins.findPlugin("com.android.application") ?: return false
        extensions.configure(
            ApplicationAndroidComponentsExtension::class.java,
        ) {
            it.onVariants { variant ->
                val transformTask = tasks.register(
                    "transformKnitFor${variant.name.replaceFirstChar(Char::uppercaseChar)}",
                    AndroidTask::class.java,
                )
                variant.artifacts
                    .forScope(ScopedArtifacts.Scope.ALL)
                    .use(transformTask)
                    .toTransform(
                        ScopedArtifact.CLASSES,
                        AndroidTask::allJars,
                        AndroidTask::allDirectories,
                        AndroidTask::output,
                    )
            }
        }
        return true
    }

    private fun Project.tryConfigJvm(): Boolean {
        val jarTasks = tasks.withType(Jar::class.java)
        if (jarTasks.isNullOrEmpty()) return false
        for (originJarTask in jarTasks) {
            val originOutput = originJarTask.archiveFile
            val originOutputFile = originOutput.get().asFile
            val newJarName = "${originOutputFile.nameWithoutExtension}WithKnit.jar"
            val newOutputFile = File(originOutputFile.parentFile, newJarName)
            val newName = "${originJarTask.name}WithKnit"
            val jvmTask = tasks.register(newName, JvmTask::class.java)
            jvmTask.configure {
                it.dependsOn(originJarTask)
                it.originJar.set(originJarTask.archiveFile)
                it.output.set(newOutputFile)
            }
        }
        return true
    }

    abstract class AndroidTask : DefaultTask() {
        @get:InputFiles
        abstract val allJars: ListProperty<RegularFile>

        @get:InputFiles
        abstract val allDirectories: ListProperty<Directory>

        @get:OutputFile
        abstract val output: RegularFileProperty

        @TaskAction
        fun taskAction() {
            val androidJarFile = findAndroidJar(project)
            val allJars = allJars.get().map { it.asFile } + androidJarFile
            val allDirs = allDirectories.get().map { it.asFile }
            val outputJarFile = output.get().asFile
            val knitTask = KnitTask(allJars, allDirs, outputJarFile, false)
            knitTask.execute()
        }
    }

    abstract class JvmTask : DefaultTask() {
        @get:InputFile
        abstract val originJar: RegularFileProperty

        @get:OutputFile
        abstract val output: RegularFileProperty

        @TaskAction
        fun taskAction() {
            val allJars = listOf(originJar.get().asFile)
            val outputJarFile = output.get().asFile
            val knitTask = KnitTask(allJars, emptyList(), outputJarFile, true)
            knitTask.execute()
        }
    }
}

private fun findAndroidJar(project: Project): File {
    val androidExtension = project.extensions.findByType(BaseExtension::class.java)
    val compileSdkVersion = androidExtension?.compileSdkVersion
    val sdkDirectory = androidExtension?.sdkDirectory
    if (compileSdkVersion == null || sdkDirectory == null) {
        throw IllegalArgumentException("please ensure you have config the compileSdkVersion.")
    }
    val jarFile = File("$sdkDirectory/platforms/$compileSdkVersion/android.jar")
    if (!jarFile.exists()) {
        throw IllegalArgumentException("cannot find android.jar which should be ${jarFile.absolutePath}.")
    }
    return jarFile
}

