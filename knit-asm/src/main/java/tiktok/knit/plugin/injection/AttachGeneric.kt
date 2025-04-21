// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.element.KnitGenericType
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
fun ProvidesMethod.attachGeneric(
    whichMatched: Int, needed: KnitType,
): ProvidesMethod {
    if (whichMatched == -1) return this

    // find params
    val originType = providesTypes[whichMatched]
    val paramMap = mutableMapOf<Int, KnitGenericType>()
    originType.getParamMap(needed, paramMap)
    if (paramMap.isEmpty()) return this

    val types = providesTypes.toMutableList()
    types[whichMatched] = needed

    return copy(
        providesTypes = types,
        requirements = requirements.map { it.transformWithMap(paramMap) },
    )
}

private fun KnitType.transformWithMap(map: Map<Int, KnitGenericType>): KnitType {
    val id = classifier.id
    if (id != null) {
        map[id]?.type?.let { return it }
    }
    val newParams = typeParams.toMutableList()
    for (i in newParams.indices) {
        val param = newParams[i]
        newParams[i] = param.transformWithMap(map)
    }
    return copy(typeParams = newParams)
}

private fun KnitGenericType.transformWithMap(map: Map<Int, KnitGenericType>): KnitGenericType {
    val id = type?.classifier?.id
    if (id != null) map[id]?.let { return it }
    return type?.transformWithMap(map)?.toGeneric() ?: this
}

private fun KnitType.getParamMap(needed: KnitType, map: MutableMap<Int, KnitGenericType>) {
    val id = classifier.id
    if (id != null) map[id] = needed.toGeneric()

    val originTP = typeParams
    val neededTP = needed.typeParams
    assert(originTP.size == neededTP.size)
    for (index in originTP.indices) {
        val originGType = originTP[index]
        val neededGType = neededTP[index]
        originGType.getParamMap(neededGType, map)
    }
}

private fun KnitGenericType.getParamMap(needed: KnitGenericType, map: MutableMap<Int, KnitGenericType>) {
    val id = type?.classifier?.id
    if (id != null) map[id] = needed
}