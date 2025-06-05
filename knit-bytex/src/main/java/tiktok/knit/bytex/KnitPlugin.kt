// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.bytex

import com.android.build.gradle.AppExtension
import com.ss.android.ugc.bytex.common.BaseExtension
import com.ss.android.ugc.bytex.pluginconfig.anno.PluginConfig
import org.gradle.api.Project

/**
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */
@PluginConfig("tiktok.knit.plugin")
class KnitPlugin : SimplePlugin<KnitPlugin.Extension, KnitContextImpl>() {
    override val action: IAction
        get() {
            return ActionImpl(context)
        }

    open class Extension : BaseExtension() {
        var needDump = true
        override fun getName(): String = "Knit"
    }

    override fun getContext(
        project: Project, android: AppExtension?, extension: Extension
    ): KnitContextImpl {
        return KnitContextImpl(project, android, extension)
    }
}