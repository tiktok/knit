// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.MultiBindingType
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.filterSuccess
import tiktok.knit.plugin.listDesc
import tiktok.knit.plugin.mapDesc
import tiktok.knit.plugin.pairClassifier
import tiktok.knit.plugin.setDesc
import tiktok.knit.plugin.success

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
object MultiBindingIF : InjectionFactory {
    override fun build(findInjectionContext: FindInjectionContext): List<Result<Injection>> {
        val requiredType = findInjectionContext.requiredType
        val bindingType = when (requiredType.classifier.desc) {
            listDesc -> MultiBindingType.L
            setDesc -> MultiBindingType.S
            mapDesc -> MultiBindingType.M
            else -> return emptyList()
        }
        val elementType = getRequiredElementType(requiredType, bindingType) ?: return emptyList()
        val findingAllElementContext = findInjectionContext.copy(
            requiredType = elementType, multiProvides = true,
        )
        val allInjections = InjectionBinder.buildInjectionFrom(findingAllElementContext)
            .filterSuccess().filter {
                validBindingType(elementType, bindingType, it.providesMethod, it.type)
            }
        if (allInjections.isEmpty()) {
            return emptyList() // no multi-binding found
        }
        val providesMethod = ProvidesMethod.fromMultibinding(bindingType)
        val injection = Injection(
            requiredType, providesMethod, allInjections, Injection.From.MULTI,
        ).success
        return listOf(injection)
    }

    private fun getRequiredElementType(requiredType: KnitType, bindingType: MultiBindingType): KnitType? {
        val typeParams = requiredType.typeParams
        when (bindingType) {
            MultiBindingType.L -> return typeParams[0].type
            MultiBindingType.S -> return typeParams[0].type
            MultiBindingType.M -> {
                val kType = typeParams[0].type ?: return null
                val vType = typeParams[1].type ?: return null
                return KnitType.from(
                    pairClassifier,
                    typeParams = listOf(
                        kType.toGeneric(), vType.toGeneric(),
                    ),
                )
            }
        }
    }

    private fun validBindingType(
        elementType: KnitType,
        bindingType: MultiBindingType,
        providesMethod: ProvidesMethod,
        providesType: KnitType,
    ): Boolean {
        val bindingName = bindingType.functionName
        if (bindingName !in providesMethod.intoTarget) return false
        if (providesType == elementType) return true
        return false
    }
}
