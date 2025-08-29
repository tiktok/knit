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
    // Optional optimistic/error status metadata
    val status: Status? = null,
    // Optional per-component delta metadata
    val delta: Delta? = null,
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
            return ComponentDump(parent.orNull(), composite.orNull(), injections?.orNull(), providers.orNull(), null, null)
        }

        val default = ComponentDump(null, null, null, null, null, null)
    }
}

data class Status(
    val error: Boolean? = null,
    val optimistic: Boolean? = null,
)

data class Delta(
    val parentsAdded: List<String>? = null,
    val parentsRemoved: List<String>? = null,
    val compositeAdded: List<String>? = null,
    val compositeRemoved: List<String>? = null,
    val injectionsAddedKeys: List<String>? = null,
    val injectionsRemovedKeys: List<String>? = null,
    val injectionsUpdatedKeys: List<String>? = null,
    val providersAdded: List<String>? = null,
    val providersRemoved: List<String>? = null,
)

typealias InjectionWithOrigin = Pair<BoundComponentClass, Injection>

/**
 * ```json5
 * {
 *   wayToMethod: "getComposite -> getFoo"
 *   methodId: "getFoo (SELF)",
 *   parameters: [
 *     {
 *       methodId: "getArg1 (COMPOSITE)"
 *     }
 *   ]
 * }
 * ```
 */
data class InjectionDump(
    /** @see [ProvidesMethod.identifier] */
    val methodId: String,
    val wayToMethod: String?,
    val parameters: List<InjectionDump>?,
) {
    companion object : CanDump<InjectionWithOrigin, InjectionDump> {
        override fun dump(origin: InjectionWithOrigin): InjectionDump {
            val (component, injection) = origin
            val method = injection.providesMethod
            val identifier = Printer.method(method)
            val way: String? = getWay(component, method)
            val type = injection.from.name
            val methodId = "$identifier ($type)"
            val requirements =
                if (injection.providesMethod.staticProvides) injection.requirementInjections
                else injection.requirementInjections.drop(1)
            val params = requirements.map { dump(component to it) }
            return InjectionDump(methodId, way, params.orNull())
        }

        private fun getWay(component: BoundComponentClass, method: ProvidesMethod): String? {
            if (component.internalName == method.containerClass) return null
            val componentWay = runCatching { component.findWay(method.containerClass) }.getOrNull() ?: return null
            val wayPaths = componentWay.map { (propAcc, _) -> propAcc.printable() } + method.functionName
            return wayPaths.joinToString(".").takeIf { it.isNotBlank() }
        }
    }
}

data class ProviderDump(
    /** @see [Printer.method] */
    val provider: String,
    /** @see [Printer.type] */
    val parameters: List<String>?,
) {
    companion object : CanDump<ProvidesMethod, ProviderDump> {
        override fun dump(origin: ProvidesMethod): ProviderDump {
            val methodDesc = Printer.method(origin)
            val params = origin.requirements.map { Printer.type(it) }
            return ProviderDump(methodDesc, params.orNull())
        }
    }
}

private fun <T> List<T>.orNull() = takeIf { it.isNotEmpty() }
private fun <K, V> Map<K, V>.orNull() = takeIf { it.isNotEmpty() }
