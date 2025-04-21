// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitSimpleError
import tiktok.knit.plugin.PropAccName
import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.injection.ComponentInjections
import tiktok.knit.plugin.knitInternalError
import tiktok.knit.plugin.wrapBuildException
import java.util.LinkedList

/**
 * this is a wrapper for [ComponentClass], but it has been bound with [injections] information and
 * it has direct reference to other [BoundComponentClass], such as [parents] & [compositeComponents]
 *
 * Created by yuejunyu on 2023/6/14
 * @author yuejunyu.0
 * @see ComponentClass
 */
class BoundComponentClass(
    val internalName: InternalName,
    val parents: List<BoundCompositeComponent>,
    val typeParams: List<KnitGenericType>,
    val provides: List<ProvidesMethod>,
    val compositeComponents: Map<PropAccName, BoundCompositeComponent>, // property access name -> component
    val injectedGetters: List<InjectedGetter>,
    val singletons: List<KnitSingleton>,
    val isInterface: Boolean,
) {
    var injections: ComponentInjections? = null

    private fun findWayInternal(
        requiredComponent: InternalName,
        path: MutableComponentWay,
    ): Boolean {
        if (internalName == requiredComponent) return true
        val parentWay = MutableComponentWay()
        if (parents.any {
                it.component.findWayInternal(requiredComponent, parentWay)
            }) {
            path.addAll(parentWay)
            return true
        }
        for ((accName: PropAccName, component: BoundCompositeComponent) in compositeComponents) {
            val pair = accName to component.component
            path.addLast(pair)
            // don't need judge component visibility again, we have proceed it before.
            if (component.component.findWayInternal(requiredComponent, path)) return true
            path.removeLast()
        }
        return false
    }

    fun findWay(
        requiredComponent: InternalName,
    ): ComponentWay {
        val mutableComponentWay = MutableComponentWay()
        val success = findWayInternal(requiredComponent, mutableComponentWay)
        if (!success) {
            knitInternalError("cannot found a way to acquire $requiredComponent from $internalName")
        }
        return mutableComponentWay.toList()
    }
}

typealias ComponentWay = List<Pair<PropAccName, BoundComponentClass>>
typealias MutableComponentWay = LinkedList<Pair<PropAccName, BoundComponentClass>>

typealias BoundComponentMapping = MutableMap<InternalName, BoundComponentClass>

fun ComponentClass.attach2BoundMapping(
    componentMapping: ComponentMapping,
    boundMapping: BoundComponentMapping,
): BoundComponentClass {
    var boundComponent = boundMapping[internalName]
    if (boundComponent == null) {
        boundComponent = attachGenericTo(
            componentMapping,
            typeParams.map { it to it },
            linkedSetOf(),
        )
        boundMapping[internalName] = boundComponent
    }
    return boundComponent
}

private fun <T> pairing(aList: List<T>, bList: List<T>): List<Pair<T, T>> {
    require(aList.size == bList.size) {
        // impossible case, add logs to investigate bugs.
        "pairing failed: \na: $aList\nb: $bList"
    }
    if (aList.isEmpty()) return emptyList()
    return buildListCompat {
        for ((index, aT) in aList.withIndex()) {
            val bT = bList[index]
            add(aT to bT)
        }
    }
}

private fun ComponentClass.attachGenericTo(
    componentMapping: ComponentMapping,
    typeParamPair: List<Pair<KnitGenericType, KnitGenericType>>, // old to new
    stack: LinkedHashSet<InternalName>,
): BoundComponentClass {
    stack += internalName
    val parentBounds: List<BoundCompositeComponent> = parents.map {
        it.wrapBuildException {
            attachGenericTo(it, componentMapping, stack)
        }
    }
    val boundCompositeComponents = compositeComponents.mapValues { (_, compositeComponent) ->
        compositeComponent.wrapBuildException {
            attachGenericTo(compositeComponent, componentMapping, stack)
        }
    }
    stack -= internalName
    // mapping parameter as real type
    val typeParamMap = HashMap<Int, KnitGenericType>()
    for ((old, new) in typeParamPair) {
        val id = old.type?.classifier?.id ?: continue
        typeParamMap[id] = new
    }
    val provides = provides.map { providesMethod ->
        val types = providesMethod.providesTypes.map {
            it.attachTypeParams(typeParamMap)
        }
        providesMethod.copy(providesTypes = types)
    }
    val newTypeParams = typeParamPair.map { it.second }
    return BoundComponentClass(
        internalName, parentBounds, newTypeParams, provides,
        boundCompositeComponents, injectedGetters, singletons, isInterface,
    )
}

private fun ComponentClass.attachGenericTo(
    compositeComponent: CompositeComponent,
    componentMapping: ComponentMapping,
    stack: LinkedHashSet<InternalName>,
): BoundCompositeComponent {
    val compositeName = compositeComponent.type.internalName
    require(compositeName !in stack) {
        "Detect a loop when construct component: ${stack.joinToString(" -> ")} -> $compositeName"
    }
    val existedComposite = componentMapping(compositeName) ?: knitInternalError("cannot find class: $this")
    val old = existedComposite.typeParams
    val new = compositeComponent.type.typeParams
    val typeParameterPairs = try {
        if (old.isEmpty()) emptyList() else pairing(old, new)
    } catch (e: IllegalArgumentException) {
        val message = "Please add the @Component annotation for ${internalName.fqn} and ${compositeName.fqn}. :)"
        throw KnitSimpleError(message, e)
    }
    val boundComponent = existedComposite.attachGenericTo(
        componentMapping, typeParameterPairs, stack,
    )
    return BoundCompositeComponent(boundComponent, compositeComponent.isPublic)
}

class BoundCompositeComponent(
    val component: BoundComponentClass,
    val isPublic: Boolean,
)

private fun KnitType.attachTypeParams(params: Map<Int, KnitGenericType>): KnitType {
    var classifier = classifier
    if (classifier.isTypeParameter()) {
        val param = params[classifier.id]?.type?.classifier
        if (param != null) classifier = param
    }
    val newTypeParams = typeParams.map { typeParam ->
        typeParam.type?.attachTypeParams(params)?.toGeneric() ?: typeParam
    }
    return KnitType.from(classifier, nullable, named, newTypeParams)
}
