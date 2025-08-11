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
        if (KnitAndroidConfig.tryConfigAndroid(target)) return
        if (target.tryConfigJvm()) return
        System.err.println("cannot found any knit target available.")
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
                it.group = GROUP
                it.dependsOn(originJarTask)
                it.originJar.set(originJarTask.archiveFile)
                it.output.set(newOutputFile)
            }
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
            val allJars = listOf(originJar.get().asFile)
            val outputJarFile = output.get().asFile
            val knitTask = KnitTask(allJars, emptyList(), outputJarFile, true)
            knitTask.execute()
        }
    }

    companion object {
        const val GROUP = "knit"
    }
}
