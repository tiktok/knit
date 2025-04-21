// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.knitInternalError
import kotlin.metadata.KmTypeParameter

/**
 * Created by yuejunyu on 2023/6/2
 * @author yuejunyu.0
 */

typealias TypeParamIdMapper = (id: Int) -> KmTypeParameter
typealias ComponentMapping = (internalName: InternalName) -> ComponentClass?

operator fun TypeParamIdMapper.plus(params: List<KmTypeParameter>): TypeParamIdMapper {
    val map = params.associateBy { it.id }
    return { it ->
        map[it] ?: this(it)
    }
}

data class InjectedGetter private constructor(
    val component: InternalName,
    val name: FuncName, // getterFunctionName
    val type: KnitType, // prop Type
) {
    val identifier: String = "$component $name ()${type.classifier.desc}"
    companion object {
        private fun KnitType.containsUnresolvedType(): Boolean {
            if (classifier.isTypeParameter()) return true
            val typeParameters = typeParams
            return typeParameters.any { it.type?.containsUnresolvedType() == true }
        }

        fun from(
            component: InternalName, name: FuncName, type: KnitType,
        ): InjectedGetter {
            require(!type.containsUnresolvedType()) {
                "in $component, we disallow to inject a type which contains type parameter. functionName: $name, type: $type"
            }
            return InjectedGetter(component, name, type)
        }
    }
}

fun List<KmTypeParameter>.toIdMapper(): TypeParamIdMapper {
    val map = associateBy { it.id }
    return { id ->
        map[id] ?: knitInternalError("parameter id $id cannot found in current parameters $this")
    }
}
