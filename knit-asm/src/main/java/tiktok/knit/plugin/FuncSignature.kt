// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.element.KnitSingleton

/**
 * Created by yuejunyu on 2023/7/9
 * @author yuejunyu.0
 */
data class FuncSignature(
    val name: String,
    val desc: String,
) {
    val identifier: String by lazy {
        var fullName = name + desc
        fullName = fullName.replace('/', '_')
        "backend_" + fullName.filter { it.isJavaIdentifierPart() }
    }

    companion object {
        fun from(singleton: KnitSingleton): FuncSignature {
            return FuncSignature(singleton.funcName, singleton.desc)
        }

        fun from(methodNode: MethodNode): FuncSignature {
            return FuncSignature(methodNode.name, methodNode.desc)
        }
    }
}