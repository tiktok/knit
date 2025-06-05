// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.FuncSignature
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.injection.Injection

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
data class InjectionWriterContext(
    val inWhichComponent: BoundComponentClass,
    val classNode: ClassNode,
    val injection: Injection,
    val getterNode: MethodNode,
    val singletonMap: Map<FuncSignature, KnitSingleton>,
    val lambdaIndex: LambdaIndex = LambdaIndex(0),
) {

    fun newLambdaIndex(): Int {
        return lambdaIndex.lambdaIndex++
    }

    class LambdaIndex(var lambdaIndex: Int)
}