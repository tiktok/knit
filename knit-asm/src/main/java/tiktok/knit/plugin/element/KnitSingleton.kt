// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.FuncDesc
import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.isStatic

/**
 * Created by yuejunyu on 2023/6/8
 * @author yuejunyu.0
 */
data class KnitSingleton(
    val global: Boolean, // global singleton or not
    val funcName: FuncName,
    val desc: FuncDesc,
    val type: KnitType,
    val threadSafe: Boolean,
) {
    fun isConstructor() = funcName == "<init>"

    init {
        if (isConstructor()) {
            // constructor must be a global, actually it could be done? but here is a simple solution
            require(global) {
                "Singleton for constructor must not be a inner class: $type"
            }
        }
    }

    companion object {
        fun from(
            global: Boolean, getterFuncName: FuncName, desc: FuncDesc,
            type: KnitType, threadSafe: Boolean,
        ): KnitSingleton = KnitSingleton(global, getterFuncName, desc, type, threadSafe)

        fun from(getterMethodNode: MethodNode, type: KnitType, threadSafe: Boolean): KnitSingleton {
            return from(
                getterMethodNode.isStatic, getterMethodNode.name, getterMethodNode.desc,
                type.forceWrapped(), threadSafe,
            )
        }

        fun fromConstructor(
            constructorNode: MethodNode, type: KnitType, threadSafe: Boolean
        ): KnitSingleton = from(
            true, "<init>", constructorNode.desc, type, threadSafe,
        )
    }
}
