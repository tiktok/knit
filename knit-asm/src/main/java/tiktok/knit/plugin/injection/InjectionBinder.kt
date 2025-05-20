// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.exactSingleInjection
import tiktok.knit.plugin.injection.checker.ComponentChecker
import tiktok.knit.plugin.injection.checker.ProvidesParentChecker

/**
 * Created by yuejunyu on 2023/6/7
 * @author yuejunyu.0
 */
object InjectionBinder {
    private val injectionFactories: Array<InjectionFactory> = arrayOf(
        DefaultIF, FactoryIF, MultiBindingIF,
    )

    private val componentChecker: Array<ComponentChecker> = arrayOf(
        ProvidesParentChecker,
    )

    fun checkComponent(
        inheritJudgement: InheritJudgement, component: BoundComponentClass
    ) = componentChecker.forEach { it.check(inheritJudgement, component) }

    fun buildInjectionFrom(
        findingContext: FindInjectionContext,
    ): List<Result<Injection>> = buildListCompat {
        for (injectionFactory in injectionFactories) {
            var singleInjections = injectionFactory.build(findingContext)
            if (!findingContext.multiProvides) {
                // if onlyCollectionProvides, they shouldn't occur in normal search
                singleInjections = singleInjections.filterNot {
                    it.getOrNull()?.providesMethod?.onlyCollectionProvides == true
                }
            }
            if (singleInjections.isNotEmpty()) addAll(singleInjections)
        }
    }

    @Deprecated("factory context replacement", ReplaceWith("buildInjectionsForComponent"))
    fun buildInjectionsForComponent(
        component: BoundComponentClass,
        globalContainer: GlobalInjectionContainer,
        inheritJudgement: InheritJudgement = InheritJudgement.AlwaysFalse,
    ) = buildInjectionsForComponent(
        component, globalContainer,
        InjectionFactoryContext(inheritJudgement),
    )

    fun buildInjectionsForComponent(
        component: BoundComponentClass,
        globalContainer: GlobalInjectionContainer,
        factoryContext: InjectionFactoryContext,
    ): ComponentInjections {
        val injectionMap = hashMapOf<FuncName, Injection>()
        val injectedGetters = component.injectedGetters
        val allProvides = CPF.all(component, true) + globalContainer.all
        for (injectedGetter in injectedGetters) {
            val (_, funcName: FuncName, fieldType: KnitType) = injectedGetter
            val allProvidesForSingleInjection = allProvides.ignoreItSelfWithParent(injectedGetter)
            val findingContext = FindInjectionContext(
                factoryContext, component,
                fieldType, allProvidesForSingleInjection, false,
            )
            val injections = buildInjectionFrom(findingContext)
            injectionMap[funcName] = injections.exactSingleInjection(
                component, fieldType,
            ) {
                CPF.all(component, true).map { it.method }
            }.getOrThrow()
        }
        return injectionMap
    }
}

/**
 * indicates a single binding
 *
 * @property type it is which type could provides by this injection
 * @property requirementInjections it will have same order with [providesMethod]'s [ProvidesMethod.requirements],
 *   but the first requirement is which component could provide corresponding [providesMethod] if this isn't a
 *   static provides.
 */
data class Injection(
    val type: KnitType,
    val providesMethod: ProvidesMethod,
    val requirementInjections: List<Injection>,
    val from: From,
) {

    enum class From {
        SELF, // provided by itself
        PARENT, // by its parent component
        COMPOSITE, // by its composite component
        GLOBAL, // by global provides
        MULTI, // multi-binding
        ;

        /** place here, because no need to import, I don't want to use extension function */
        operator fun invoke(providesMethod: ProvidesMethod): SourcedMethod {
            return SourcedMethod(this, providesMethod)
        }
    }

    constructor(
        type: KnitType, providesMethod: ProvidesMethod, from: From,
    ) : this(type, providesMethod, emptyList(), from)
}

typealias ComponentInjections = Map<FuncName, Injection>
