// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Created by yuejunyu on 2025/4/18
 * @author yuejunyu.0
 */
class KnitTask(
    private val jarInputs: List<File>,
    private val dirInputs: List<File>,
    private val outputJar: File,
    private val useJrt: Boolean,
) {
    fun execute() {
        val jarOutput = JarOutputStream(
            BufferedOutputStream(FileOutputStream(outputJar)),
        )
        val graphPipeline = GraphPipeline()
        val knitPipeline = KnitPipeline(useJrt)

        val container = ContentContainer()
        val allClasses = container.getAllClasses(jarInputs, dirInputs, jarOutput)
        for (classAccessor in allClasses) {
            val classNode = classAccessor.getNode()
            graphPipeline.traverse(classNode)
            knitPipeline.traverse(classNode)
        }
        graphPipeline.traverseFinished()
        val graph = graphPipeline.graph()
        knitPipeline.beforeTransform(graph)
        for (classAccessor in allClasses) {
            val classNode = classAccessor.getNode()
            knitPipeline.transform(classNode)
            jarOutput.putNextEntry(JarEntry(classAccessor.clazzFileName))
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            classNode.accept(classWriter)
            jarOutput.write(classWriter.toByteArray())
            jarOutput.closeEntry()
        }

        container.closeAll()
        jarOutput.close()
    }
}

private class ClassAccessor(
    val clazzFileName: String,
    val getNode: () -> ClassNode,
)

private fun InputStream.asNode(): ClassNode {
    val node = ClassNode()
    val reader = ClassReader(this)
    reader.accept(node, 0)
    return node
}

private class ContentContainer {
    private val inputStreams = mutableListOf<Closeable>()

    fun getAllClasses(
        allJars: List<File>,
        allDirectories: List<File>,
        output: JarOutputStream,
    ): List<ClassAccessor> = sequence {
        allJars.forEach { file ->
            println("handling " + file.absolutePath)
            val jarFile = JarFile(file)
            for (entry in jarFile.entries()) {
                if (!entry.name.endsWith(".class")) {
                    // direct copy
                    val input = jarFile.getInputStream(entry)
                    val content = input.use { it.readAllBytes() }
                    output.putNextEntry(entry)
                    output.write(content)
                    output.closeEntry()
                    continue
                }
                val getClassNode: () -> ClassNode = {
                    jarFile.getInputStream(entry).use(InputStream::asNode)
                }
                yield(ClassAccessor(entry.name, getClassNode))
            }
            inputStreams += jarFile
        }
        allDirectories.forEach { directory ->
            println("handling " + directory.absolutePath)
            for (file in directory.walk()) {
                if (!file.name.endsWith(".class")) continue
                val getClassNode: () -> ClassNode = {
                    file.inputStream().use(InputStream::asNode)
                }
                yield(ClassAccessor(file.name, getClassNode))
            }
        }
    }.toList()

    fun closeAll() {
        inputStreams.forEach {
            kotlin.runCatching { it.close() }
        }
    }
}
