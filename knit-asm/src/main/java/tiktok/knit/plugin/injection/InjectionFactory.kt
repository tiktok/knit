// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.InjectedGetter
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
interface InjectionFactory {
    fun build(
        findInjectionContext: FindInjectionContext
    ): List<Result<Injection>>
}

typealias SourcedMethod = Pair<Injection.From, ProvidesMethod>

inline val SourcedMethod.method get() = second
inline val SourcedMethod.from get() = first

// ignore matched self injection to avoid sof
fun Collection<SourcedMethod>.ignoreItSelf(selfIdentifier: String): Collection<SourcedMethod> {
    val newMethods = filterNot { it.method.identifier == selfIdentifier }
    if (newMethods.size == size) return this // list not changed
    return newMethods
}

// ignore matched self & parent self injection to avoid sof
fun Collection<SourcedMethod>.ignoreItSelfWithParent(injectGetter: InjectedGetter): Collection<SourcedMethod> {
    val newMethods = ArrayList<SourcedMethod>(size)
    for (sourceMethod in this) {
        val (from, method) = sourceMethod
        // skip same identifier
        if (method.identifier == injectGetter.identifier) continue
        if (from == Injection.From.PARENT) {
            // skip extended
            if (method.functionName == injectGetter.name && method.requirements.isEmpty()) continue
        }
        newMethods += sourceMethod
    }
    if (newMethods.size == size) return this // list not changed
    return newMethods
}

/** Injection finder context for build single injection */
data class FindInjectionContext(
    val factoryContext: InjectionFactoryContext,
    val component: BoundComponentClass,
    val requiredType: KnitType,
    val allProvides: Collection<SourcedMethod>,
    val multiProvides: Boolean, // true if this search is search for multi-provides
)

/** factory context for single factory, available for build every injection */
class InjectionFactoryContext(
    val inheritJudgement: InheritJudgement,
) : InheritJudgement by inheritJudgement
