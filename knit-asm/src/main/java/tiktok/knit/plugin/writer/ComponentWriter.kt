// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.FuncSignature
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.knitInternalError

/**
 * Created by yuejunyu on 2023/6/13
 * @author yuejunyu.0
 */
class ComponentWriter(private val context: KnitContext) {
    private val componentMap = context.boundComponentMap

    fun write(node: ClassNode) {
        val component = componentMap[node.name] ?: return
        val componentInjections = component.injections
            ?: knitInternalError("Unexpected error! ${node.name} couldn't find correct injections, this file didn't found in bytex incremental input!")
        // non global singletons (like constructor or static function)
        val singletons = component.singletons.filterNot { it.global }
        val singletonMap = singletons.associateBy { FuncSignature.from(it) }

        writeSingletonBackingFields(node, singletonMap)

        val methods = node.methods.toList()
        for (methodNode in methods) {
            val injection = componentInjections[methodNode.name]
            if (injection != null) {
                // injected through by di
                writeGetterNode(
                    component, node, injection, methodNode, singletonMap,
                )
                continue
            }
            // normal singleton way
            val signature = FuncSignature.from(methodNode)
            val singleton = singletonMap[signature]
            if (singleton != null) writeExistedProvidesSingleton(
                node, methodNode, singleton,
            )
        }
        // view model logic, it has special injection
        generateVMLogic(context, node, component)
    }

    private fun writeGetterNode(
        inWhichComponent: BoundComponentClass,
        classNode: ClassNode, injection: Injection, getterNode: MethodNode,
        singletonMap: Map<FuncSignature, KnitSingleton>,
    ) {
        val injectContext = InjectionWriterContext(
            inWhichComponent, classNode, injection, getterNode, singletonMap,
        )
        val insnList = InsnList()
        val generatedCatchNodes = DepsWriter.writeGetterInstant(insnList, injectContext)
        val tryCatchNodes = getterNode.tryCatchBlocks.orEmpty() + generatedCatchNodes
        getterNode.instructions = insnList
        getterNode.tryCatchBlocks = tryCatchNodes
    }
}
