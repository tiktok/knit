// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.bytex

/**
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */
import com.android.build.api.transform.Status
import com.ss.android.ugc.bytex.common.BaseContext
import com.ss.android.ugc.bytex.common.BaseExtension
import com.ss.android.ugc.bytex.common.CommonPlugin
import com.ss.android.ugc.bytex.transformer.TransformEngine
import com.ss.android.ugc.bytex.transformer.cache.FileData
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.Logger

abstract class SimplePlugin<E : BaseExtension, X : BaseContext<E>> : CommonPlugin<E, X>() {
    abstract val action: IAction

    override fun traverse(relativePath: String, node: ClassNode) {
        super.traverse(relativePath, node)
        action.traverseAdd(relativePath, node)
    }

    override fun init(transformer: TransformEngine) {
        super.init(transformer)
        action.init(transformer)
        val requestStart = System.currentTimeMillis()
        action.excludeIncrementalFiles().forEach {
            context.transformContext.requestNotIncremental(it)
        }
        val requestEnd = System.currentTimeMillis()
        Logger.i("Knit requestNotIncremental cost ${requestEnd - requestStart}ms")
    }

    override fun traverseIncremental(fileData: FileData, node: ClassNode?) {
        super.traverseIncremental(fileData, node)
        when (fileData.status) {
            Status.ADDED -> node?.let {
                action.traverseAdd(fileData.relativePath, node)
            }

            Status.REMOVED -> action.traverseRemove(fileData)
            Status.CHANGED -> node?.let {
                action.traverseChange(fileData, node)
            }

            else -> {}
        }
    }

    override fun transform(relativePath: String, node: ClassNode): Boolean {
        action.transform(relativePath, node)
        return super.transform(relativePath, node)
    }

    override fun beforeTransform(engine: TransformEngine) {
        super.beforeTransform(engine)
        action.beforeTransform(engine)
    }

    override fun afterTransform(engine: TransformEngine) {
        super.afterTransform(engine)
        action.afterTransform(engine)
    }
}
