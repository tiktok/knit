// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.element.ProvidesMethod

class GlobalInjectionContainer(
    allComponents: Collection<BoundComponentClass>
) {
    constructor() : this(emptyList())

    private val _all: Set<SourcedMethod> = allComponents.asSequence()
        .flatMap { it.provides.asSequence() }
        .filter { it.staticProvides }
        .map { Injection.From.GLOBAL(it) }
        .toSortedSet(sourcedMethodComparator)

    val all: Set<SourcedMethod> = _all.filterNotTo(LinkedHashSet()) { it.method.isVM() }
        .toSortedSet(sourcedMethodComparator)
    val allVM: Set<SourcedMethod> = _all.filterTo(LinkedHashSet()) { it.method.isVM() }
        .toSortedSet(sourcedMethodComparator)

    val allSingletons: Map<InternalName, List<KnitSingleton>> = allComponents
        .associate { it.internalName to it.singletons }
        .mapValues { entry -> entry.value.filter { it.global } }
        .toSortedMap()

    val vmProviders by lazy { getAllVmProviders() }

    // provides vm interface name -> all provides method for this
    private fun getAllVmProviders(): Map<InternalName, List<ProvidesMethod>> {
        val result: MutableMap<InternalName, MutableList<ProvidesMethod>> = hashMapOf()
        for ((_, providesMethod) in allVM) {
            val providesTypes = providesMethod.providesTypes
            for (providesType in providesTypes) {
                val methods = result.getOrPut(providesType.internalName) { mutableListOf() }
                methods += providesMethod
            }
        }
        return result.toSortedMap()
    }
}

private val sourcedMethodComparator = compareBy<SourcedMethod>(
    { it.method.identifier },
    { it.from },
)
