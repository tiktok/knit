// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.function0Desc
import tiktok.knit.plugin.success

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
object FactoryIF : InjectionFactory {
    override fun build(findInjectionContext: FindInjectionContext): List<Result<Injection>> {
        val requiredType = findInjectionContext.requiredType
        val component = findInjectionContext.component
        if (!requiredType.isFunction0()) return emptyList()
        val returnType = requiredType.typeParams.first().type ?: return emptyList()
        val innerFindingContext = findInjectionContext.copy(
            requiredType = returnType,
        )
        val originInjections = InjectionBinder.buildInjectionFrom(innerFindingContext)
        if (originInjections.isEmpty()) return emptyList()
        return originInjections.map { injectionResult ->
            val injection = injectionResult.getOrElse { return@map Result.failure(it) }
            val oldPriority = injection.providesMethod.priority
            val providesMethod = methodFromLambda(component.internalName, returnType, oldPriority)
            Injection(requiredType, providesMethod, listOf(injection), injection.from).success
        }
    }

    private fun methodFromLambda(
        containerClass: InternalName,
        providesType: KnitType,
        oldPriority: Int,
    ): ProvidesMethod = ProvidesMethod.from(
        containerClass = containerClass,
        desc = ProvidesMethod.TAG_LAMBDA,
        functionName = ProvidesMethod.TAG_LAMBDA,
        actualType = providesType,
        priority = oldPriority,
    )
}

private fun KnitType.isFunction0(): Boolean {
    return classifier.desc == function0Desc
}
