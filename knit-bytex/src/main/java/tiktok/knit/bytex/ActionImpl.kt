// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.bytex

import com.ss.android.ugc.bytex.common.graph.Graph
import com.ss.android.ugc.bytex.transformer.TransformEngine
import com.ss.android.ugc.bytex.transformer.cache.FileData
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.CollectInfo
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.dump.KnitDumper
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.writer.ComponentWriter
import tiktok.knit.plugin.writer.GlobalProvidesWriter
import java.io.File

/**
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */
class ActionImpl(private val knit: KnitContextImpl) : IAction {
    private val collectInfo = CollectInfo(knit)
    private val writer by lazy { ComponentWriter(knit) }
    private val dumper by lazy { KnitDumper() }
    private val globalProvidesWriter by lazy { GlobalProvidesWriter(knit) }
    private val removedInternals = linkedSetOf<InternalName>()
    private val updatedInternals = linkedSetOf<InternalName>()

    override fun init(transformer: TransformEngine) {
        knit.recovery()
    }

    override fun traverseAdd(relativePath: String, classNode: ClassNode) {
        collectInfo.collect(classNode)
    // Mark added/changed class for incremental dump
    dumper.update(classNode.name)
    updatedInternals += classNode.name
    }

    override fun traverseRemove(fileData: FileData) {
        val className: InternalName = fileData.relativePath.removeSuffix(".class")
        knit.componentMap.remove(className)
        dumper.remove(className)
    removedInternals += className
    }

    override fun traverseChange(fileData: FileData, classNode: ClassNode) {
        knit.componentMap.remove(classNode.name)
        dumper.remove(classNode.name)
        collectInfo.collect(classNode)
    // Mark changed class for incremental dump
    dumper.update(classNode.name)
    updatedInternals += classNode.name
    }

    override fun beforeTransform(engine: TransformEngine) {
        val graph: Graph = knit.classGraph
        val inheritJudgement = InjectionBinderBytex.ByteXGraphInheritJudgement(graph)
        knit.inheritJudgement = inheritJudgement

        // knit main
        InjectionBinderBytex.buildBindingForAll(knit)
    }

    override fun transform(relativePath: String, classNode: ClassNode) {
        if (classNode.name == globalProvidesInternalName) {
            globalProvidesWriter.write(classNode)
        } else {
            writer.write(classNode)
        }
    }

    override fun afterTransform(engine: TransformEngine) {
        val storage = knit.save()
        if (knit.extension.needDump) {
            // Produce a verification delta log when incremental
            if (knit.isIncremental) {
                val deltaLog = File(knit.workDir, "knit.delta.log")
                // changed files in this run (relativePath) and dependency-expanded set
                val changed = knit.notIncrementalFiles
                deltaLog.bufferedWriter().use {
                    it.appendLine("Knit incremental delta (plan):")
                    it.appendLine("- changedOrAffectedCount: ${changed.size}")
                    it.appendLine("- changedOrAffected:")
                    changed.forEach { f -> it.appendLine("  - $f") }
                    it.appendLine("- removedCount: ${removedInternals.size}")
                    removedInternals.forEach { n -> it.appendLine("  - REMOVED ${n}") }
                    it.appendLine("- updatedCount: ${updatedInternals.size}")
                    updatedInternals.forEach { n -> it.appendLine("  - UPDATED ${n}") }
                }
            }
            dumper.dumpContext(knit, File(knit.workDir, "knit-dump.json"), knit.isIncremental)
        }
    }

    override fun excludeIncrementalFiles(): Collection<String> {
        return knit.requestNotIncrementalFiles
    }
}

interface IAction {
    fun init(transformer: TransformEngine)
    fun traverseAdd(relativePath: String, classNode: ClassNode)
    fun traverseRemove(fileData: FileData)
    fun traverseChange(fileData: FileData, classNode: ClassNode)

    fun beforeTransform(engine: TransformEngine)
    fun transform(relativePath: String, classNode: ClassNode)
    fun afterTransform(engine: TransformEngine)
    fun excludeIncrementalFiles(): Collection<String>
}
