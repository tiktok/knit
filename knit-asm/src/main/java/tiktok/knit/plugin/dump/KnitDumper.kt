// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.dump

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import tiktok.knit.plugin.KnitContext
import java.io.File

/**
 * Created at 2024/4/17
 * @author yuejunyu.0
 */
object KnitDumper {
    fun dumpContext(context: KnitContext, file: File) {
        if (file.exists()) file.delete()
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val components = context.boundComponentMap.values
        val result = components.map { ComponentDump.dump(it) }
        file.bufferedWriter().use {
            provideGson().toJson(result, it)
        }
    }

    private fun provideGson() : Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
}