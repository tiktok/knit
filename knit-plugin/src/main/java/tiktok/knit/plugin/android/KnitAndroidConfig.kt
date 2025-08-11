package tiktok.knit.plugin.android

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import tiktok.knit.plugin.KnitTask
import java.io.File

/**
 * Created by junyu on 2025/8/11
 * @author yuejunyu.0@tiktok.com
 */
internal object KnitAndroidConfig {
    fun tryConfigAndroid(project: Project): Boolean {
        project.plugins.findPlugin("com.android.application") ?: return false
        project.extensions.configure(
            ApplicationAndroidComponentsExtension::class.java,
        ) {
            it.onVariants { variant ->
                val transformTask = project.tasks.register(
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

}
