// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.dump.KnitDumper.ComponentDumps
import tiktok.knit.plugin.dump.ComponentDump
import tiktok.knit.plugin.dump.Delta
import tiktok.knit.plugin.dump.Status
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
    private val bindingErrors: MutableList<String> = mutableListOf()
    private val errorComponents: MutableSet<InternalName> = linkedSetOf()


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
            try {
                InjectionBinder.checkComponent(inheritJudgement, bound)
                val injections = InjectionBinder.buildInjectionsForComponent(
                    bound, context.globalInjectionContainer,
                    InjectionFactoryContext(inheritJudgement),
                )
                bound.injections = injections
            } catch (t: Throwable) {
                val compName = bound.internalName
                val msg = "binding failed for $compName: ${t.message ?: t.javaClass.simpleName}"
                Logger.w(msg, t)
                bindingErrors += msg
                errorComponents += compName
                // Continue without injections so downstream dump can still proceed
                bound.injections = hashMapOf()
            }
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

        // Compute deltas and optimistic views
        val prevKeys = prev?.keys?.toSet().orEmpty()
        val nowKeys = now.keys.toSet()
        val removed = prevKeys - nowKeys
        val added = nowKeys - prevKeys
        val common = prevKeys intersect nowKeys
        val updated = common.filter { key -> prev?.get(key) != now[key] }

        // Per-component delta embedding + status flags
        for (key in nowKeys) {
            val cur = now[key] ?: continue
            val old = prev?.get(key)
            val parentsAdded = (cur.parent.orEmpty() - old?.parent.orEmpty()).orEmpty()
            val parentsRemoved = (old?.parent.orEmpty() - cur.parent.orEmpty()).orEmpty()
            val compositeAdded = (cur.composite?.keys.orEmpty() - old?.composite?.keys.orEmpty()).toList()
            val compositeRemoved = (old?.composite?.keys.orEmpty() - cur.composite?.keys.orEmpty()).toList()
            val injAdded = (cur.injections?.keys.orEmpty() - old?.injections?.keys.orEmpty()).toList()
            val injRemoved = (old?.injections?.keys.orEmpty() - cur.injections?.keys.orEmpty()).toList()
            val injUpdated = (cur.injections?.keys.orEmpty().intersect(old?.injections?.keys.orEmpty())
                .filter { k -> old?.injections?.get(k) != cur.injections?.get(k) }).toList()
            val provAdded = (cur.providers.orEmpty() - old?.providers.orEmpty()).map { it.provider }
            val provRemoved = (old?.providers.orEmpty() - cur.providers.orEmpty()).map { it.provider }

            val status = Status(
                error = key in errorComponents,
                optimistic = errorComponents.isNotEmpty(),
            )
            val delta = Delta(
                parentsAdded = parentsAdded.takeIf { it.isNotEmpty() },
                parentsRemoved = parentsRemoved.takeIf { it.isNotEmpty() },
                compositeAdded = compositeAdded.takeIf { it.isNotEmpty() },
                compositeRemoved = compositeRemoved.takeIf { it.isNotEmpty() },
                injectionsAddedKeys = injAdded.takeIf { it.isNotEmpty() },
                injectionsRemovedKeys = injRemoved.takeIf { it.isNotEmpty() },
                injectionsUpdatedKeys = injUpdated.takeIf { it.isNotEmpty() },
                providersAdded = provAdded.takeIf { it.isNotEmpty() },
                providersRemoved = provRemoved.takeIf { it.isNotEmpty() },
            )

            now[key] = cur.copy(status = status.takeIf { it.error == true || it.optimistic == true }, delta = delta)
        }

        // Build change-only dump (same structure as knit.json): only changed keys with status/delta
        val changesOnly = ComponentDumps().apply {
            // removed
            removed.forEach { key ->
                this[key] = ComponentDump.default.copy(
                    status = Status(
                        error = false,
                        optimistic = errorComponents.isNotEmpty(),
                        removed = true,
                        added = false,
                    ),
                    delta = Delta(
                        parentsRemoved = prev?.get(key)?.parent,
                        compositeRemoved = prev?.get(key)?.composite?.keys?.toList(),
                        injectionsRemovedKeys = prev?.get(key)?.injections?.keys?.toList(),
                        providersRemoved = prev?.get(key)?.providers?.map { it.provider },
                    ),
                )
            }
            // added
            added.forEach { key ->
                val cur = now[key]
                if (cur != null) {
                    this[key] = cur.copy(
                        status = (cur.status ?: Status()).copy(
                            added = true,
                            removed = false,
                            optimistic = errorComponents.isNotEmpty(),
                            error = (cur.status?.error == true) || (key in errorComponents),
                        ),
                        delta = (cur.delta ?: Delta()).copy(
                            parentsAdded = cur.parent,
                            compositeAdded = cur.composite?.keys?.toList(),
                            injectionsAddedKeys = cur.injections?.keys?.toList(),
                            providersAdded = cur.providers?.map { it.provider },
                        ),
                    )
                }
            }
            // updated
            updated.forEach { key ->
                val cur = now[key]
                if (cur != null) {
                    this[key] = cur.copy(
                        status = (cur.status ?: Status()).copy(
                            optimistic = errorComponents.isNotEmpty(),
                            error = (cur.status?.error == true) || (key in errorComponents),
                            added = false,
                            removed = false,
                        ),
                        delta = cur.delta,
                    )
                }
            }
        }

        // Persist change-only dump to a timestamped file
        val changesDir = File(dumpOutputFile.parentFile, "changes").apply { mkdirs() }
        val ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(java.time.LocalDateTime.now())
        val changeFile = File(changesDir, "knit-change-$ts.json")
        changeFile.bufferedWriter().use { gson.toJson(changesOnly, it) }

        // Write a machine-friendly batch metadata sidecar file
        run {
            val meta = linkedMapOf<String, Any>(
                "timestamp" to java.time.Instant.now().toString(),
                "baselineExists" to (prev != null),
                "optimistic" to (errorComponents.isNotEmpty()),
                "changeFile" to changeFile.name,
                "counts" to mapOf(
                    "added" to added.size,
                    "removed" to removed.size,
                    "updated" to updated.size,
                    "errorAffected" to errorComponents.size,
                ),
                "added" to added.sorted(),
                "removed" to removed.sorted(),
                "updated" to updated.sorted(),
                "errorAffected" to errorComponents.map { it }.sorted(),
            )
            // Write timestamped meta
            File(changesDir, "knit-change-$ts.meta.json").bufferedWriter().use { gson.toJson(meta, it) }
            // Update 'latest' pointers for fast watcher discovery (no symlinks for cross-platform safety)
            File(changesDir, "latest.meta.json").bufferedWriter().use { gson.toJson(meta, it) }
            File(changesDir, "latest.txt").writeText(changeFile.name)
        }

        // Persist baseline dump only if none exists yet
        if (!dumpOutputFile.parentFile.exists()) dumpOutputFile.parentFile.mkdirs()
        if (prev == null) {
            dumpOutputFile.bufferedWriter().use { gson.toJson(now, it) }
        }
        val dumpDuration = System.currentTimeMillis() - dumpStart
        Logger.i("dump knit info cost: ${dumpDuration}ms")

    // No separate error/delta logs; errors are reflected via status within the change file
    }
}
