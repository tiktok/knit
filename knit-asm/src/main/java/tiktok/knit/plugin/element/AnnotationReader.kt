// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import knit.IntoList
import knit.KnitExperimental
import knit.Priority
import knit.Provides
import knit.Singleton
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.MultiBindingType
import tiktok.knit.plugin.allAnnotations
import tiktok.knit.plugin.callName
import tiktok.knit.plugin.intoListDesc
import tiktok.knit.plugin.intoMapDesc
import tiktok.knit.plugin.intoSetDesc
import tiktok.knit.plugin.knitVMAnnotationDesc
import tiktok.knit.plugin.onEach
import tiktok.knit.plugin.priorityDesc
import tiktok.knit.plugin.providesDesc
import tiktok.knit.plugin.singletonDesc

/**
 * Created by yuejunyu on 2023/6/8
 * @author yuejunyu.0
 */
object AnnotationReader {
    fun getProvidesAnnotationInfo(
        methodNode: MethodNode,
    ): ProvidesAnnotationInfo {
        return getProvidesAnnotationInfo(methodNode.allAnnotations)
    }

    fun getProvidesAnnotationInfo(
        allAnnotations: List<AnnotationNode>,
    ): ProvidesAnnotationInfo {
        return ProvidesAnnotationInfo.from(allAnnotations)
    }

    private val singletonThreadSafeCallName = Singleton::threadsafe.callName
    private val isSingletonCallName = Singleton::isSingleton.callName
    private val defaultSingleton = Singleton()

    /** read [Singleton] annotation info from its annotation AsmNode */
    fun readSingleton(
        annotationNode: AnnotationNode?,
    ): Singleton? {
        if (annotationNode?.desc != singletonDesc) return null
        var isSingleton = defaultSingleton.isSingleton
        var isThreadSafe = defaultSingleton.threadsafe
        annotationNode.onEach { attrName, value ->
            when (attrName) {
                singletonThreadSafeCallName -> isThreadSafe = value.cast()
                isSingletonCallName -> isSingleton = value.cast()
            }
        }
        return Singleton(isThreadSafe, isSingleton) // return default value
    }
}

data class ProvidesAnnotationInfo(
    val providesTypes: List<KnitType>,
    val intoTarget: String,
    val onlyCollectionProvides: Boolean,
    val priority: Int,
) {
    companion object {
        fun from(allAnnotations: List<AnnotationNode>): ProvidesAnnotationInfo {
            var declaredProvides: List<KnitType> = listOf()
            var intoTarget = ""
            var onlyCollectionProvides = false
            var priority = 0
            for (annotation in allAnnotations) when (annotation.desc) {
                providesDesc -> declaredProvides = getDeclaredProvides(annotation)
                knitVMAnnotationDesc -> declaredProvides = getDeclaredProvides(annotation)
                priorityDesc -> priority = getPriority(annotation)
                intoListDesc -> {
                    intoTarget += MultiBindingType.L.functionName
                    onlyCollectionProvides = onlyCollectionProvides || getOnlyCollection(annotation)
                }

                intoSetDesc -> {
                    intoTarget += MultiBindingType.S.functionName
                    onlyCollectionProvides = onlyCollectionProvides || getOnlyCollection(annotation)
                }

                intoMapDesc -> {
                    intoTarget += MultiBindingType.M.functionName
                    onlyCollectionProvides = onlyCollectionProvides || getOnlyCollection(annotation)
                }
            }
            return ProvidesAnnotationInfo(
                declaredProvides, intoTarget, onlyCollectionProvides, priority
            )
        }

        private val providesParentCallName = Provides::parents.callName
        private fun getDeclaredProvides(annotationNode: AnnotationNode): List<KnitType> {
            var providesTypeNames: List<Type> = emptyList()
            annotationNode.onEach { attrName, value ->
                if (attrName == providesParentCallName) {
                    providesTypeNames = value.cast()
                }
            }
            return providesTypeNames.map { KnitType.from(KnitClassifier.from(it), false) }
        }

        private val onlyCollectionProvidesCallName = IntoList::onlyCollectionProvides.callName
        private fun getOnlyCollection(annotationNode: AnnotationNode): Boolean {
            var onlyCollectionProvides = false
            annotationNode.onEach { attrName, value ->
                if (attrName == onlyCollectionProvidesCallName) onlyCollectionProvides = value.cast()
            }
            return onlyCollectionProvides
        }

        @OptIn(KnitExperimental::class)
        private val priorityCallName = Priority::priority.callName
        private fun getPriority(annotationNode: AnnotationNode): Int {
            var priority = 0
            annotationNode.onEach { attrName, value ->
                if (attrName == priorityCallName) priority = value.cast()
            }
            return priority
        }
    }
}