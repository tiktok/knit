// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

/**
 * represent one of [ComponentClass.compositeComponents] for [ComponentClass]
 *
 * Created by yuejunyu on 2023/6/20
 * @author yuejunyu.0
 */
data class CompositeComponent(
    val type: KnitType,
    val isPublic: Boolean,
) {
    constructor(type: KnitType) : this(type, true)
    init {
        val args = type.typeParams
        for (arg in args) {
            validateArg(arg)
        }
    }
}

private fun validateArg(arg: KnitGenericType) {
    require(arg.variance == KnitGenericType.NO_VARIANCE) {
        "composite component must not includes variance"
    }
    val type = arg.type ?: return
    for (typeParam in type.typeParams) {
        validateArg(typeParam)
    }
}
