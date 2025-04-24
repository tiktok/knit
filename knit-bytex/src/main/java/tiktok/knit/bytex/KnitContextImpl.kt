// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.bytex

import com.android.build.gradle.AppExtension
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.ss.android.ugc.bytex.common.BaseContext
import org.gradle.api.Project
import tiktok.knit.plugin.ILogger
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.Logger
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.Injection
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by yuejunyu on 2023/6/5
 * @author yuejunyu.0
 */
class KnitContextImpl(
    project: Project, android: AppExtension?, extension: KnitPlugin.Extension
) : BaseContext<KnitPlugin.Extension>(project, android, extension), KnitContext {
    override val componentMap = ConcurrentHashMap<InternalName, ComponentClass>()
    override val boundComponentMap = ConcurrentHashMap<InternalName, BoundComponentClass>()

    // consumer -> [producer]
    private val backDepsMap: HashMap<InternalName, MutableSet<InternalName>> = hashMapOf()

    // follows injected by transform process
    override lateinit var globalInjectionContainer: GlobalInjectionContainer
    override lateinit var inheritJudgement: InheritJudgement

    val workDir: File get() = buildDir()
    private val cacheFile get() = File(workDir, cacheName)

    val isIncremental get() = transformContext.isIncremental

    /** all files that knit have requested not to incremental. */
    lateinit var requestNotIncrementalFiles: Collection<String>

    /** all files that will be proceeded in this bytex process. */
    lateinit var notIncrementalFiles: Collection<String>

    override fun init() {
        super.init()
        val logger = logger
        Logger.delegate = object : ILogger {
            override fun i(tag: String, information: String) {
                logger.i(tag, information)
            }

            override fun w(tag: String, warning: String, t: Throwable?) {
                logger.w(tag, warning, t)
            }

            override fun e(tag: String, error: String, t: Throwable?) {
                logger.e(tag, error, t)
            }
        }
    }

    override fun releaseContext() {
        super.releaseContext()
        Logger.delegate = null
    }

    fun recovery(): Storage? {
        componentMap.clear()
        backDepsMap.clear()
        // don't recovery cache if no cache or non-incremental
        val hasCache = isIncremental && cacheFile.exists()
        if (!hasCache) {
            if (cacheFile.exists()) cacheFile.delete()
            updateIncrementalInfo()
            return null
        }
        // has cache, recovery it
        val storage = cacheFile.bufferedReader().use {
            try {
                gson.fromJson(it, Storage::class.java)
            } catch (e: JsonParseException) {
                System.err.println("Knit cache is invalid, request not incremental due to invalid json detected: ${e.message}")
                cacheFile.delete()
                updateIncrementalInfo()
                transformContext.requestNotIncremental()
                return null
            }
        }
        componentMap.putAll(storage.componentMap)
        backDepsMap.putAll(storage.backDepsMap)
        // set incremental info
        updateIncrementalInfo()
        return storage
    }

    private fun updateIncrementalInfo() {
        requestNotIncrementalFiles = excludeIncrementalFiles()
        notIncrementalFiles =
            (transformContext.changedFiles.map { it.relativePath } + requestNotIncrementalFiles).toSet()
    }

    fun save(): Storage {
        val backDepsMap = backDepsMap
        for (boundComponent in boundComponentMap.values) {
            val thisComponentName = boundComponent.internalName
            val allDeps = boundComponent.allDeps().toList()
            // adds backward deps
            for (dep in allDeps) {
                val exist = backDepsMap[dep]
                if (exist == null) {
                    backDepsMap[dep] = hashSetOf(thisComponentName)
                } else {
                    exist += thisComponentName
                }
            }
        }
        val storage = Storage(componentMap, backDepsMap)
        cacheFile.bufferedWriter().use {
            gson.toJson(storage, it)
        }
        return storage
    }

    private fun BoundComponentClass.allDeps(): Sequence<InternalName> = sequence {
        val values = injections?.values ?: return@sequence
        for (injection in values) {
            yieldAll(injection.allDeps())
        }
    }

    private fun Injection.allDeps(): Sequence<InternalName> = sequence {
        yield(providesMethod.containerClass)
        for (requirementInjection in requirementInjections) {
            yieldAll(requirementInjection.allDeps())
        }
    }


    private fun excludeIncrementalFiles(): Collection<String> {
        val depsMap = backDepsMap
        val allNonIncrementalFiles = LinkedHashSet<String>()
        // global provides should always be non-incremental
        allNonIncrementalFiles += globalProvidesClass
        // calculate all non-incremental files which affected by changed files
        val changedFiles = transformContext.changedFiles
        for (changedFile in changedFiles) {
            val path = changedFile?.relativePath ?: continue
            val internalName = path.substringBefore(".class")
            depsMap[internalName]?.forEach { producer ->
                allNonIncrementalFiles += "$producer.class"
            }
            allNonIncrementalFiles += calculateVMRelatedClasses(internalName).map { "$it.class" }
        }
        return allNonIncrementalFiles
    }

    private fun calculateVMRelatedClasses(changedClass: String): Collection<InternalName> {
        val componentMap = componentMap
        return componentMap.filter {
            changedClass in it.value.injectedVmTypes
        }.keys
    }

    class Storage(
        val componentMap: Map<InternalName, ComponentClass>,
        val backDepsMap: Map<InternalName, MutableSet<InternalName>>,
    )
}

private val gson by lazy { Gson() }
private const val cacheName = "knit-cache.json"
private val globalProvidesClass = "$globalProvidesInternalName.class"
