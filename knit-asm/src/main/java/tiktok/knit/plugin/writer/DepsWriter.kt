// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.TryCatchBlockNode
import tiktok.knit.plugin.FuncSignature
import tiktok.knit.plugin.PropAccName
import tiktok.knit.plugin.aload
import tiktok.knit.plugin.basicTypeIndex
import tiktok.knit.plugin.basicTypes
import tiktok.knit.plugin.box
import tiktok.knit.plugin.checkCast
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.getField
import tiktok.knit.plugin.getFieldName
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.invokeStatic
import tiktok.knit.plugin.invokeVirtualOrInterface
import tiktok.knit.plugin.isGetter
import tiktok.knit.plugin.mbBuilderInternalName
import tiktok.knit.plugin.newArray
import tiktok.knit.plugin.objectInternalName
import tiktok.knit.plugin.pushToArray
import tiktok.knit.plugin.typedReturn
import tiktok.knit.plugin.unbox

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
object DepsWriter {
    fun writeGetterInstant(
        insnList: InsnList,
        injectContext: InjectionWriterContext
    ): List<TryCatchBlockNode> {
        val (_, classNode, _, getterNode, singletonMap) = injectContext
        val signature = FuncSignature.from(getterNode)
        val singleton = singletonMap[signature]

        return if (singleton != null) {
            insnList.writeSingletonInjection(
                classNode.name, singleton, 1,
                injectContext.injection.type,
            ) {
                buildDeps(this, injectContext)
            }
        } else {
            val injectedType = injectContext.injection.type
            buildDeps(insnList, injectContext)
            insnList.typedReturn(injectedType.basicTypeIndex())
            emptyList()
        }
    }

    fun buildDeps(
        insnList: InsnList,
        injectContext: InjectionWriterContext,
    ) = with(insnList) {
        val injection = injectContext.injection
        val method = injection.providesMethod
        val isLambda = method.isLambda()
        if (isLambda) {
            writeFactoryLambda(injectContext)
            return
        } else if (method.isVM()) {
            // vm will not process here
            return
        }
        val inWhichComponent = injectContext.inWhichComponent
        val thisComponentName = inWhichComponent.internalName
        val providesOwner = method.containerClass
        if (method.staticProvides) {
            for (requirementInjection in injection.requirementInjections) {
                buildRequirement(injectContext, requirementInjection)
            }
            val globalCallDesc = method.globalCallDesc()
            invokeStatic(globalProvidesInternalName, method.globalBytecodeIdentifier(), globalCallDesc)
            checkCast(method.actualType.forceWrapped().internalName)
        } else if (method.isMultiBinding()) {
            val requirementInjections = injection.requirementInjections
            newArray(requirementInjections.size, objectInternalName)
            for ((i, requirementInjection) in requirementInjections.withIndex()) {
                pushToArray(i) {
                    buildRequirement(injectContext, requirementInjection)
                }
            }
            invokeStatic(mbBuilderInternalName, method.functionName, method.desc)
        } else {
            // non-static, provides this component
            aload(0)
            if (providesOwner != thisComponentName) {
                // cannot provide from this component, we will find a way to construct providesOwner
                val way = inWhichComponent.findWay(providesOwner)
                var owner = inWhichComponent
                for ((propAcc: PropAccName, component: BoundComponentClass) in way) {
                    val componentName = component.internalName
                    if (propAcc.isGetter()) {
                        invokeVirtualOrInterface(
                            owner.internalName, propAcc, "()L$componentName;", owner.isInterface,
                        )
                    } else {
                        val propName = propAcc.getFieldName()
                        getField(owner.internalName, propName, "L$componentName;")
                    }
                    owner = component
                }
            }
            // push deps
            for (requirementInjection in injection.requirementInjections.drop(1)) {
                buildRequirement(injectContext, requirementInjection)
            }
            invokeVirtualOrInterface(
                providesOwner, method.functionName, method.desc, method.interfaceProvides,
            )
            val basicTypeIndex = basicTypes.indexOf(method.actualType.classifier.desc)
            if (basicTypeIndex >= 0) {
                box(basicTypeIndex)
            }
        }
    }

    private fun InsnList.buildRequirement(
        injectContext: InjectionWriterContext,
        requirementInjection: Injection
    ) {
        buildDeps(this, injectContext.copy(injection = requirementInjection))
        val requirementInjectedType = requirementInjection.type
        val requirementTypeIndex = requirementInjectedType.basicTypeIndex()
        if (requirementTypeIndex >= 0) {
            unbox(requirementTypeIndex)
        }
    }
}

