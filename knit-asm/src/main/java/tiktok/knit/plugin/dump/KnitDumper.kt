// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.dump

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.Logger
import tiktok.knit.plugin.fqn
import java.io.File

/**
 * Created at 2024/4/17
 * @author yuejunyu.0
 */
class KnitDumper {
    private val removedClasses = arrayListOf<String>()
    private val updatedClasses = arrayListOf<String>()

    fun remove(className: InternalName) {
        removedClasses.add(className.fqn)
    }

    /** Marks a component (by internal name) to be (re)dumped in incremental mode. */
    fun update(className: InternalName) {
        updatedClasses.add(className.fqn)
    }

    fun dumpContext(context: KnitContext, file: File, incremental: Boolean = false) {
        if (file.exists() && !incremental) file.delete()
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        val dumpStart = System.currentTimeMillis()
        val gson = provideGson()

        val dumps = if (!incremental) ComponentDumps() else {
            file.bufferedReader().use {
                gson.fromJson(it, ComponentDumps::class.java)
            }
        }
        for (className in removedClasses) {
            dumps.remove(className)
        }

        // Determine which keys to (re)dump
        val keysToDump: Collection<InternalName> = if (incremental && updatedClasses.isNotEmpty()) {
            // Only dump the updated set (already in FQN). Filter to those we still have in memory.
            updatedClasses.filter { context.boundComponentMap.containsKey(it) }
        } else {
            // Full dump of all currently known components
            context.boundComponentMap.keys
        }

        // Info log about incremental behavior
        if (incremental) {
            Logger.i("KnitDumper incremental: removed=${removedClasses.size}, updated=${if (updatedClasses.isEmpty()) "ALL(${keysToDump.size})" else updatedClasses.size}")
        } else {
            Logger.i("KnitDumper full dump: components=${keysToDump.size}")
        }

        for (name in keysToDump) {
            val component = context.boundComponentMap[name] ?: continue
            dumps[name] = kotlin.runCatching { ComponentDump.dump(component) }
                .onFailure { it.printStackTrace() }
                .onFailure { Logger.e("dump $name failed: ${it.message}", it) }
                .getOrElse { ComponentDump.default }
        }
        file.bufferedWriter().use {
            provideGson().toJson(dumps, it)
        }
        val dumpDuration = System.currentTimeMillis() - dumpStart
        Logger.i("dump knit info cost: ${dumpDuration}ms")

        // clear state for next use within the same build session
        removedClasses.clear()
        updatedClasses.clear()
    }

    private fun provideGson() : Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    class ComponentDumps : LinkedHashMap<InternalName, ComponentDump>()
}