// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.element.ComponentClass

/**
 * Created by yuejunyu on 2023/6/5
 * @author yuejunyu.0
 */
class CollectInfo(knitContext: KnitContext) {
    private val componentMap = knitContext.componentMap
    fun collect(node: ClassNode) {
        if (!needProcess(node)) return
        val container = node.asMetadataContainer()
        if (container == null) {
            Logger.w("Found Component ${node.name}, but it didn't contains kotlin Metadata! (maybe a Java file.)", null)
            return
        }
        val componentClass = ComponentClass.from(container)
        componentMap[componentClass.internalName] = componentClass
    }

    companion object {
        @Suppress("RedundantIf")
        fun needProcess(node: ClassNode): Boolean {
            // class contains @Provides/@Component
            if (node.allAnnotations.any { it.desc in componentClassMarkerAnnotations }) return true
            // any methods contain annotations
            node.methods.forEach { methodNode ->
                if (methodNode.allAnnotations.any { it.desc in methodMarkerAnnotations }) return true
            }
            // any field should be injected
            if (node.fields.any { it.desc in autoComponentDelegates }) return true
            return false
        }
    }
}

private val componentClassMarkerAnnotations = arrayOf(
    componentDesc,
    providesDesc,
    knitVMAnnotationDesc, // @KnitViewModel same as @Provides
)

private val methodMarkerAnnotations = arrayOf(
    componentDesc,
    providesDesc,
    knitVMAnnotationDesc, // @KnitViewModel same as @Provides
)

// some field's type will auto make its parent as a Component
private val autoComponentDelegates = arrayOf(
    diStubDesc,
    diMutStubDesc,
    knitVMLazyDesc,
)
