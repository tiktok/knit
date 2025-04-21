// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.dump

import tiktok.knit.plugin.Printer
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.printable

/**
 * Created at 2024/4/17
 * @author yuejunyu.0
 */

/**
 * ```text
 * FooComponent {
 *   parent: [
 *     Bar1, Bar2
 *   ]
 *   composite: {
 *     prop1: Prop1Composite
 *     prop2: Prop2Composite
 *   }
 *   injections: {
 *     injected1: {...}
 *     injected2: {...}
 *   }
 * }
 * ```
 */
data class ComponentDump(
    val parent: List<String>?,
    val composite: Map<String, String>?,
    val injections: Map<String, InjectionDump>?,
    val providers: List<ProviderDump>?,
) {
    companion object : CanDump<BoundComponentClass, ComponentDump> {
        override fun dump(origin: BoundComponentClass): ComponentDump {
            val parent = origin.parents.map { it.component.internalName.fqn }
            val composite = origin.compositeComponents.map { (propAcc, component) ->
                val visibilityPrefix = if (component.isPublic) "public " else "private "
                (visibilityPrefix + propAcc.printable()) to component.component.internalName.fqn
            }.toMap()
            val injections = origin.injections?.map { (propAcc, injection) ->
                val dump = InjectionDump.dump(origin to injection)
                propAcc.printable() to dump
            }?.toMap()
            val providers = origin.provides.map { ProviderDump.dump(it) }
            return ComponentDump(parent.orNull(), composite.orNull(), injections?.orNull(), providers.orNull())
        }
    }
}

typealias InjectionWithOrigin = Pair<BoundComponentClass, Injection>

/**
 * ```json5
 * {
 *   way: "getComposite -> getFoo"
 *   from: "(SELF) getFoo",
 *   params: [
 *     {
 *       from: "(COMPOSITE) getArg1"
 *     }
 *   ]
 * }
 * ```
 */
data class InjectionDump(
    /** @see [ProvidesMethod.identifier] */
    val from: String,
    val way: String?,
    val params: List<InjectionDump>?,
) {
    companion object : CanDump<InjectionWithOrigin, InjectionDump> {
        override fun dump(origin: InjectionWithOrigin): InjectionDump {
            val (component, injection) = origin
            val method = injection.providesMethod
            val identifier = Printer.method(method)
            val way: String? = if (component.internalName == method.containerClass) null
            else {
                val wayPaths = component.findWay(method.containerClass).map { (propAcc, _) ->
                    propAcc.printable()
                } + method.functionName
                wayPaths.joinToString(".").takeIf { it.isNotBlank() }
            }
            val type = injection.from.name
            val from = "($type) $identifier"
            val requirements =
                if (injection.providesMethod.staticProvides) injection.requirementInjections
                else injection.requirementInjections.drop(1)
            val params = requirements.map { dump(component to it) }
            return InjectionDump(from, way, params.orNull())
        }
    }
}

data class ProviderDump(
    /** @see [Printer.method] */
    val desc: String,
    /** @see [Printer.type] */
    val params: List<String>?,
) {
    companion object : CanDump<ProvidesMethod, ProviderDump> {
        override fun dump(origin: ProvidesMethod): ProviderDump {
            val desc = Printer.method(origin)
            val params = origin.requirements.map { Printer.type(it) }
            return ProviderDump(desc, params.orNull())
        }
    }
}

private fun <T> List<T>.orNull() = takeIf { it.isNotEmpty() }
private fun <K, V> Map<K, V>.orNull() = takeIf { it.isNotEmpty() }
