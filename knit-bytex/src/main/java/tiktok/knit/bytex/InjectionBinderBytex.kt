// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.bytex

import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.Logger
import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.BoundComponentMapping
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ComponentMapping
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.attach2BoundMapping
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.InjectionBinder
import tiktok.knit.plugin.injection.InjectionFactoryContext
import com.ss.android.ugc.bytex.common.graph.Graph as ByteXGraph
import com.ss.android.ugc.bytex.common.graph.Node as ByteXNode

/**
 * Created by yuejunyu on 2023/7/5
 * @author yuejunyu.0
 */
object InjectionBinderBytex {
    fun buildBindingForAll(
        context: KnitContextImpl,
    ) {
        val start = System.currentTimeMillis()
        val componentMap = context.componentMap
        val graph: ByteXGraph = context.classGraph
        val inheritJudgement = context.inheritJudgement

        val boundComponentMap = context.boundComponentMap
        // because knit context cannot auto-inject to bytex context, so we inject it here.
        // ByteX hard to write unit-test... and I don't want to use mock frameworks
        context.globalInjectionContainer = GlobalInjectionContainer(
            componentMap.values.attach2BoundMapping(graph, boundComponentMap),
        )

        var boundComponents: Collection<BoundComponentClass> = boundComponentMap.values
        if (context.isIncremental) {
            // incremental, only bind injections for needed
            boundComponents = boundComponents.filter {
                "${it.internalName}.class" in context.notIncrementalFiles
            }
        }
        for (bound in boundComponents) {
            InjectionBinder.checkComponent(inheritJudgement, bound)
            val injections = InjectionBinder.buildInjectionsForComponent(
                bound, context.globalInjectionContainer,
                InjectionFactoryContext(inheritJudgement),
            )
            bound.injections = injections
        }

        val end = System.currentTimeMillis()
        Logger.i("buildBindingForAll cost ${end - start}ms")
    }


    class ByteXGraphInheritJudgement(
        private val byteXGraph: ByteXGraph,
    ) : InheritJudgement {
        override fun inherit(thisName: InternalName, parentName: InternalName): Boolean {
            if (thisName == parentName) return true
            return byteXGraph.inherit(thisName, parentName)
        }
    }

    class ByteXComponentMapping(
        private val byteXGraph: ByteXGraph,
        private val componentMap: Map<InternalName, ComponentClass>,
    ) : ComponentMapping {
        override fun invoke(internalName: InternalName): ComponentClass? {
            val existed = componentMap[internalName]
            if (existed != null) return existed
            val node = byteXGraph.get(internalName) ?: return null
            return node.toComponentClass()
        }

        private fun ByteXNode.toComponentClass(): ComponentClass {
            val parents = buildListCompat {
                interfaces.orEmpty().mapNotNullTo(this) {
                    it?.entity?.name
                }
                val parentClass = parent?.entity?.name
                if (parentClass != null) {
                    add(parentClass)
                }
            }.map {
                CompositeComponent(
                    KnitType.from(KnitClassifier.from(it)),
                )
            }
            return ComponentClass(entity.name, parents)
        }
    }

    internal fun Collection<ComponentClass>.attach2BoundMapping(
        graph: ByteXGraph, map: BoundComponentMapping
    ): List<BoundComponentClass> {
        val componentMap = associateBy { it.internalName }
        val mapping = ByteXComponentMapping(graph, componentMap)
        return map {
            it.attach2BoundMapping(mapping, map)
        }
    }
}
