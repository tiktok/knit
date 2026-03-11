//  Copyright (c) 2026 by TikTok Ltd., All rights reserved.
//  Licensed under the Apache License Version 2.0 that can be found in the
//  LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection.checker

import tiktok.knit.plugin.CircularDependencyException
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.injection.ComponentInjections
import tiktok.knit.plugin.injection.Injection

/**
 * Created by junyu on 2026/3/11
 * @author yuejunyu.0@tiktok.com
 */
object InjectionChecker {
    fun check(component: BoundComponentClass, injections: ComponentInjections) {
        for ((propGetter, injection) in injections) {
            val providesMethod = injection.providesMethod
            val callStack = ArrayDeque(listOf(injection.providesMethod))
            val checkContext = CheckContext(
                component,
                currentPropGetter = propGetter,
                injection = injection,
                injections = injections,
                callStack = callStack,
            )
            for (requirementInjection in injection.requirementInjections) {
                check(checkContext.copy(injection = requirementInjection))
            }
        }
    }

    private data class CheckContext(
        // static information
        val component: BoundComponentClass,
        val currentPropGetter: String,
        val injections: ComponentInjections,
        // dynamic information changing during the traverse
        val injection: Injection, // current scan injection
        val callStack: ArrayDeque<ProvidesMethod> // current provides stack
    )

    private fun check(
        context: CheckContext,
    ) {
        val injection: Injection = context.injection
        val callStack: ArrayDeque<ProvidesMethod> = context.callStack
        val component: BoundComponentClass = context.component
        val currentPropGetter = context.currentPropGetter
        val providesMethod = injection.providesMethod
        if (currentPropGetter == providesMethod.functionName) {
            callStack.addLast(providesMethod)
            throw CircularDependencyException(component.internalName.fqn, currentPropGetter, callStack.toList())
        }

        // 1. check injected getter -> connect injections through function getter
        val injectionThroughGetter = context.injections[providesMethod.functionName]
        if (injectionThroughGetter != null) {
            check(context.copy(injection = injectionThroughGetter))
        }

        // 2. check requirements
        callStack.addLast(providesMethod)
        for (requirementInjection in injection.requirementInjections) {
            check(context.copy(injection = requirementInjection))
        }
        callStack.removeLast()
    }
}