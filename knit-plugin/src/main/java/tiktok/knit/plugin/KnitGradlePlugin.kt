// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import tiktok.knit.plugin.android.KnitAndroidConfig
import java.io.File

/**
 * Created by yuejunyu on 2025/4/15
 * @author yuejunyu.0
 */
abstract class KnitGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("KnitExtension", KnitExtension::class.java)
        extension.dependencyTreeOutputPath.convention("build/knit/dependency-tree.json")
        if (KnitAndroidConfig.tryConfigAndroid(target)) return
        if (target.tryConfigJvm()) return
        System.err.println("cannot found any knit target available.")
    }

    private fun Project.tryConfigJvm(): Boolean {
        val jarTasks = tasks.withType(Jar::class.java)
        if (jarTasks.isEmpty()) return false
        for (originJarTask in jarTasks) {
            val originOutput = originJarTask.archiveFile
            val originOutputFile = originOutput.get().asFile
            val newJarName = "${originOutputFile.nameWithoutExtension}WithKnit.jar"
            val newOutputFile = File(originOutputFile.parentFile, newJarName)
            val newName = "${originJarTask.name}WithKnit"
            val jvmTask = tasks.register(newName, JvmTask::class.java)
            jvmTask.configure {
                it.group = GROUP
                it.dependsOn(originJarTask)
                it.originJar.set(originJarTask.archiveFile)
                it.output.set(newOutputFile)
            }
        }

        // Standalone dump task: scans compiled classes directly and writes JSON + delta log.
        val dumpTask = tasks.register("knitDump", DumpTask::class.java)
        dumpTask.configure {
            it.group = GROUP
            val ext = extensions.getByType(KnitExtension::class.java)
            it.dumpOutput.set(file(ext.dependencyTreeOutputPath))
        }
        return true
    }

    abstract class JvmTask : DefaultTask() {
        @get:InputFile
        abstract val originJar: RegularFileProperty

        @get:OutputFile
        abstract val output: RegularFileProperty

        @TaskAction
        fun taskAction() {
            val dumpOutputFile = project.file(
                project.extensions.getByType(KnitExtension::class.java).dependencyTreeOutputPath,
            )
            val allJars = listOf(originJar.get().asFile)
            val outputJarFile = output.get().asFile
            val knitTask = KnitTask(
                allJars, emptyList(), outputJarFile, true,
                dumpOutput = dumpOutputFile,
            )
            knitTask.execute()
        }
    }

    abstract class DumpTask : DefaultTask() {
        @get:OutputFile
        abstract val dumpOutput: RegularFileProperty

        @TaskAction
        fun taskAction() {
            val project = project
            // Default to Kotlin main classes directory
            val classDir = project.layout.buildDirectory.dir("classes/kotlin/main").get().asFile
            val tmpJar = project.layout.buildDirectory.file("tmp/knitDump/knit-dump.jar").get().asFile
            tmpJar.parentFile.mkdirs()
            val dumpFile = dumpOutput.get().asFile
            val knitTask = KnitTask(
                jarInputs = emptyList(),
                dirInputs = listOf(classDir).filter { it.exists() },
                outputJar = tmpJar,
                useJrt = true,
                dumpOutput = dumpFile,
            )
            knitTask.execute()
        }
    }

    companion object {
        const val GROUP = "knit"
    }
}
