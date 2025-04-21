// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.Type
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod

/**
 * Created by yuejunyu on 2023/10/23
 * @author yuejunyu.0
 */
object Printer {
    fun method(providesMethod: ProvidesMethod): String {
        val priorityStr = if (providesMethod.priority == 0) "" else "priority: ${providesMethod.priority} "
        val funcHead = "${providesMethod.containerClass.fqn}.${providesMethod.functionName}"
        return "$priorityStr$funcHead -> ${
            providesMethod.providesTypes.joinToString()
        }"
    }

    fun type(knitType: KnitType): String {
        with(knitType) {
            return buildString {
                if (named.isNotEmpty()) {
                    append("@Named($named) ")
                }
                append(classifier(classifier))
                if (typeParams.isNotEmpty()) {
                    append('<')
                    typeParams.joinTo(this)
                    append('>')
                }
                if (nullable) append('?')
            }
        }
    }

    fun classifier(knitClassifier: KnitClassifier): String {
        return if (knitClassifier.isTypeParameter()) "T${knitClassifier.id}" else knitClassifier.desc.d2fqn
    }

    private val InternalName.fqn: QualifiedName get() = Type.getObjectType(this).className
    private val DescName.d2fqn: QualifiedName get() = Type.getType(this).className
}