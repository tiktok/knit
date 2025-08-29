// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.dump.KnitDumper.ComponentDumps
import tiktok.knit.plugin.dump.ComponentDump
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.BoundComponentMapping
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ComponentMapping
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.attach2BoundMapping
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.InjectionBinder
import tiktok.knit.plugin.injection.InjectionFactoryContext
import tiktok.knit.plugin.writer.ComponentWriter
import tiktok.knit.plugin.writer.GlobalProvidesWriter
import java.io.File
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Created by yuejunyu on 2025/4/15
 * @author yuejunyu.0
 */
class KnitPipeline(
    private val useJrt: Boolean,
    private val dumpOutputFile: File,
) {
    private class KnitContextImpl : KnitContext {
        override val componentMap: MutableMap<InternalName, ComponentClass> = mutableMapOf()
        override val boundComponentMap: MutableMap<InternalName, BoundComponentClass> = mutableMapOf()
        override lateinit var globalInjectionContainer: GlobalInjectionContainer
        override lateinit var inheritJudgement: InheritJudgement
    }

    init {
        Logger.delegate = object : ILogger {
            override fun i(tag: String, information: String) {
                println("$tag: $information")
            }

            override fun w(tag: String, warning: String, t: Throwable?) {
                System.err.println("Warning: $tag: $warning")
            }

            override fun e(tag: String, error: String, t: Throwable?) {
                System.err.println("Error: $tag: $error")
                t?.printStackTrace()
            }
        }
    }

    private val knitContextImpl = KnitContextImpl()
    private val collectInfo = CollectInfo(knitContextImpl)
    private val writer by lazy { ComponentWriter(knitContextImpl) }
    private val globalProvidesWriter by lazy { GlobalProvidesWriter(knitContextImpl) }


    fun traverse(classNode: ClassNode) {
        collectInfo.collect(classNode)
    }

    fun beforeTransform(graph: GraphPipeline.Graph) {
        val start = System.currentTimeMillis()
        val inheritJudgement = graph.inheritJudgement
        knitContextImpl.inheritJudgement = inheritJudgement
        val context = knitContextImpl
        val componentMap = context.componentMap

        val boundComponentMap = context.boundComponentMap
        // because knit context cannot auto-inject to bytex context, so we inject it here.
        // ByteX hard to write unit-test... and I don't want to use mock frameworks
        context.globalInjectionContainer = GlobalInjectionContainer(
            componentMap.values.attach2BoundMapping(graph, boundComponentMap),
        )

        val boundComponents: Collection<BoundComponentClass> = boundComponentMap.values
        for (bound in boundComponents) {
            InjectionBinder.checkComponent(inheritJudgement, bound)
            val injections = InjectionBinder.buildInjectionsForComponent(
                bound, context.globalInjectionContainer,
                InjectionFactoryContext(inheritJudgement),
            )
            bound.injections = injections
        }

        val end = System.currentTimeMillis()
        Logger.i("buildBindingForAll cost ${end - start}ms")
    }

    fun transform(classNode: ClassNode) {
        if (classNode.name == globalProvidesInternalName) {
            globalProvidesWriter.write(classNode)
        } else {
            writer.write(classNode)
        }
    }

    class ComponentMappingImpl(
        private val useRuntimeResolve: Boolean,
        private val graph: GraphPipeline.Graph,
        private val componentMap: Map<InternalName, ComponentClass>,
    ) : ComponentMapping {
        override fun invoke(internalName: InternalName): ComponentClass {
            val existed = componentMap[internalName]
            if (existed != null) return existed
            var parents: List<InternalName> = graph.entityMap[internalName]
                ?.superClasses?.map { it.className }.orEmpty()
            if (useRuntimeResolve && parents.isEmpty()) parents = kotlin.runCatching {
                val tryRuntime = Class.forName(internalName.fqn)
                val superClass = listOfNotNull(tryRuntime.superclass)
                val implClasses = tryRuntime.interfaces.filterNotNull()
                val supers = superClass + implClasses
                supers.map { Type.getType(it).internalName }
            }.getOrNull().orEmpty()
            val compositeParents = parents.map {
                CompositeComponent(
                    KnitType.from(KnitClassifier.from(it)),
                )
            }
            return ComponentClass(internalName, compositeParents)
        }
    }

    private fun Collection<ComponentClass>.attach2BoundMapping(
        graph: GraphPipeline.Graph, map: BoundComponentMapping
    ): List<BoundComponentClass> {
        val componentMap = associateBy { it.internalName }
        val mapping = ComponentMappingImpl(useJrt, graph, componentMap)
        return map {
            it.attach2BoundMapping(mapping, map)
        }
    }

    fun finish() {
        val dumpStart = System.currentTimeMillis()

        // Build current dump in memory
        val now = ComponentDumps().apply {
            for ((name, component) in knitContextImpl.boundComponentMap) {
                this[name] = kotlin.runCatching { ComponentDump.dump(component) }
                    .getOrElse { ComponentDump.default }
            }
        }

        // Load previous dump if present
        val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val prev: ComponentDumps? = dumpOutputFile.takeIf { it.exists() }?.let {
            kotlin.runCatching { it.bufferedReader().use { r -> gson.fromJson(r, ComponentDumps::class.java) } }
                .getOrNull()
        }

        // Write delta log (derived)
        if (prev != null) {
            val prevKeys = prev.keys.toSet()
            val nowKeys = now.keys.toSet()
            val removed = prevKeys - nowKeys
            val added = nowKeys - prevKeys
            val common = prevKeys intersect nowKeys
            val updated = common.filter { key -> prev[key] != now[key] }

            val deltaLog = File(dumpOutputFile.parentFile, "knit.delta.log")
            deltaLog.bufferedWriter().use { w ->
                w.appendLine("Knit incremental delta (derived):")
                w.appendLine("- removedCount: ${removed.size}")
                removed.sorted().forEach { w.appendLine("  - REMOVED $it") }
                w.appendLine("- addedCount: ${added.size}")
                added.sorted().forEach { w.appendLine("  - ADDED $it") }
                w.appendLine("- updatedCount: ${updated.size}")
                updated.sorted().forEach { w.appendLine("  - UPDATED $it") }
            }
        }

        // Persist current dump
        if (!dumpOutputFile.parentFile.exists()) dumpOutputFile.parentFile.mkdirs()
        dumpOutputFile.bufferedWriter().use { gson.toJson(now, it) }
        val dumpDuration = System.currentTimeMillis() - dumpStart
        Logger.i("dump knit info cost: ${dumpDuration}ms")
    }
}
