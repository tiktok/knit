// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.Logger
import tiktok.knit.plugin.NoProvidesFoundException
import tiktok.knit.plugin.TypeConflictWBR
import tiktok.knit.plugin.combine
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.exactSingleInjection
import tiktok.knit.plugin.success

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
object DefaultIF : InjectionFactory {
    override fun build(findInjectionContext: FindInjectionContext): List<Result<Injection>> {
        val (factoryContext, component, requiredType, allProvides, multiProvides) = findInjectionContext
        val injections = allProvides.mapNotNull { sourcedMethod ->
            val singleMethodFindingContext = FindInjectionContext(
                factoryContext, component, requiredType, allProvides, multiProvides,
            )
            sourcedMethod.buildInjection(singleMethodFindingContext)
        }.filterNot {
            // vm cannot inject as normal, we only collect not vm things
            it.getOrNull()?.providesMethod?.isVM() == true
        }
        return injections
    }
}

/**
 * null -> cannot found provider
 * fail -> found provider, but didn't match requirements
 * success -> found provider and requirements matched
 */
private fun SourcedMethod.buildInjection(
    context: FindInjectionContext,
): Result<Injection>? {
    val (factoryContext, component, requiredType, allProvides, multiProvides) = context
    val method = method
    val allProvideTypes = method.providesTypes
    var couldProvides = false
    var whichMatched: Int = -1
    for ((i, singleProvideType) in allProvideTypes.withIndex()) {
        if (singleProvideType.availableFor(requiredType, factoryContext)) {
            // this provides method could provide required type, so we use it
            couldProvides = true
            whichMatched = i
            break
        }
    }
    if (!couldProvides) return null

    // could provides, validate requirements
    var rootProvides = from(method.attachGeneric(whichMatched, requiredType))
    var requirementAllProvides: Collection<SourcedMethod> = if (rootProvides == this) allProvides.also {
        // use origin object if no generic attached
        rootProvides = this
    } else allProvides.toMutableSet().apply {
        // replace origin one to attached one
        remove(this@buildInjection)
        add(rootProvides)
    }

    // ignore provides by itself
    requirementAllProvides = requirementAllProvides.ignoreItSelf(rootProvides.method.identifier)
    val requirementInjections = arrayListOf<Result<Injection>>()
    if (!method.staticProvides) {
        // push first as for component itself
        requirementInjections += Injection(
            method.providesTypes.first(), method, from,
        ).success
    }

    val rootMethod = rootProvides.method
    for (type in rootMethod.requirements) {
        val requirementFindingContext = FindInjectionContext(
            factoryContext, component, type, requirementAllProvides, multiProvides,
        )
        val requirementInjection = findRequirementInjection(rootMethod, requirementFindingContext)
        requirementInjections += requirementInjection
    }

    val requirementInjectionValues = requirementInjections.combine().getOrElse {
        return Result.failure(it)
    }
    return Injection(requiredType, method, requirementInjectionValues, from).success
}

private fun findRequirementInjection(
    rootMethod: ProvidesMethod,
    requirementFindingContext: FindInjectionContext,
): Result<Injection> {
    val injections = InjectionBinder.buildInjectionFrom(requirementFindingContext)
    val (_, component, requiredType, allProvides) = requirementFindingContext
    val requirementInjection = injections.exactSingleInjection(
        component, requiredType,
        onEmpty = {
            NoProvidesFoundException(
                component.internalName, requiredType,
                allProvides.filter { !it.method.staticProvides }.map { it.method },
            ).also {
                Logger.w("found provider ${rootMethod.logName} but cannot match requirement type: $requiredType", it)
            }
        },
        onTypeConflict = { _ ->
            val validInjections = injections.mapNotNull { it.getOrNull() }
            TypeConflictWBR(
                rootMethod, component.internalName, requiredType,
                validInjections.map { it.providesMethod },
            ).also {
                Logger.e("build injection meet exception", it)
            }
        },
    ) {
        allProvides.filter { !it.method.staticProvides }.map { it.method }
    }
    return requirementInjection
}
