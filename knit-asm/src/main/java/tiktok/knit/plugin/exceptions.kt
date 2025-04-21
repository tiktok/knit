// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import com.google.common.collect.HashMultimap
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.injection.Injection
import java.util.LinkedList

/**
 * Created by yuejunyu on 2023/9/5
 * @author yuejunyu.0
 */
class TypeConflictException(
    val componentInternalName: InternalName,
    val type: KnitType,
    val providesMethods: List<ProvidesMethod>,
) : IllegalStateException() {
    override val message: String
        get() = buildString {
            if (componentInternalName.isNotBlank()) {
                append("In component ${componentInternalName.fqn}, ")
            }
            append("Type providers has conflicts in different composite component.\n\n")

            append("Type: ${type}, conflict providers for this component:\n\n")
            providesMethods.joinTo(this, separator = "\n") { it.logName }
        }
}

data class TypeConflictInCompositeException(
    private val componentName: InternalName,
    private val records: CompositeRecords,
    private val type: KnitType,
    private val injections: List<Injection>,
) : IllegalStateException() {
    init {
        require(injections.size > 1)
    }

    constructor(
        component: BoundComponentClass,
        type: KnitType,
        injections: List<Injection>,
    ) : this(
        component.internalName, component.getCompositeRecords(),
        type, injections,
    )

    constructor(
        typeConflictException: TypeConflictException,
        records: CompositeRecords,
    ) : this(
        typeConflictException.componentInternalName,
        records,
        typeConflictException.type,
        typeConflictException.providesMethods.map {
            Injection(
                typeConflictException.type,
                it, Injection.From.COMPOSITE,
            )
        },
    )

    override val message: String
        get() = buildString {
            var firstNotCompositeIndex = injections.indexOfFirst { it.from != Injection.From.COMPOSITE }
            if (firstNotCompositeIndex == -1) firstNotCompositeIndex = injections.size
            val records = records
            val allComposite = injections.subList(0, firstNotCompositeIndex)
                .distinctBy { it.providesMethod }
            if (componentName.isNotBlank()) {
                append("In component ${componentName.fqn}, ")
            }
            append("Multiple(${injections.size}) type injections has conflicts in different composite component.\n\n")

            append("Type: ${type}, conflict providers for this component:\n\n")

            val lastIndex = allComposite.lastIndex
            for ((i, injection) in allComposite.withIndex()) {
                injectionLog(injection)
                val paths = records[injection.providesMethod.containerClass]
                if (paths.isNullOrEmpty()) {
                    if (i != lastIndex) append("\n")
                    continue
                } else {
                    append("\n")
                }
                val functionName = injection.providesMethod.functionName
                if (paths.size == 1) {
                    append("  the way to this injection: ")
                    append(paths.first().pathStr(functionName))
                } else {
                    append("  there has multiple paths to provides this injection: ")
                    paths.map { path ->
                        path.pathStr(injection.providesMethod.functionName)
                    }.sorted().joinTo(this, separator = ",\n    ", prefix = "[\n    ", postfix = "\n  ]")
                }
                if (i != lastIndex) append("\n")
            }
        }
}

typealias CompositeRecords = HashMultimap<InternalName, ComponentRecord>

class ComponentRecord(
    private val name: InternalName,
    private val way: List<PropAccName>,
) {
    fun pathStr(functionName: String?): String {
        val fullWay = if (functionName == null) way else (way + functionName)
        return fullWay.joinToString(separator = " -> ") { propAcc ->
            propAcc.printable()
        }
    }

    companion object {
        fun from(records: Collection<ComponentRecord>): CompositeRecords {
            val recordHashMultimap: CompositeRecords = HashMultimap.create()
            for (record in records) {
                recordHashMultimap.put(record.name, record)
            }
            return recordHashMultimap
        }
    }
}

private fun BoundComponentClass.getCompositeRecords(): CompositeRecords {
    val allComposite = allComposite(LinkedList(), true)
    return ComponentRecord.from(allComposite)
}

private fun BoundComponentClass.allComposite(
    currentWay: LinkedList<PropAccName>, includePrivate: Boolean,
): List<ComponentRecord> = buildListCompat {
    for ((acc, compositeComponent) in compositeComponents) {
        currentWay.addLast(acc)
        if (compositeComponent.isPublic || includePrivate) {
            add(ComponentRecord(compositeComponent.component.internalName, currentWay.toList()))
            addAll(compositeComponent.component.allComposite(currentWay, false))
        }
        currentWay.removeLast()
    }
}

typealias TypeConflictWBR = TypeConflictWhenBuildRequirementException

class TypeConflictWhenBuildRequirementException(
    buildRequirementFor: ProvidesMethod,
    private val componentInternalName: InternalName,
    private val type: KnitType,
    private val providesMethods: List<ProvidesMethod>,
) : IllegalStateException() {
    private val m = buildRequirementFor
    override val message: String
        get() = buildString {
            if (componentInternalName.isNotBlank()) {
                append("In component ${componentInternalName.fqn}\n")
            }
            append("When build requirements of ${m.containerClass.fqn}.${m.functionName} ${m.desc} \n\n")

            append("Type providers has conflicts in different composite component,\n")
            append("Type: ${type}, conflict providers for this component:\n\n")

            providesMethods.joinTo(this, separator = "\n") { it.logName }
        }
}

data class NoProvidesFoundException(
    private val componentInternalName: InternalName,
    private val type: KnitType,
    private val providers: List<ProvidesMethod>,
) : IllegalStateException() {
    override val message: String
        get() = buildString {
            if (componentInternalName.isNotBlank()) {
                append("In component ${componentInternalName.fqn}, ")
            }
            append("Couldn't found type provides method or cannot access type provides.\n")
            append("Type: ${type}, all providers in this component:\n")
            append(providers.joinToString("\n", prefix = "{ ", postfix = " }") { it.logName })
        }
}


class CombinedInjectionException(
    list: List<Throwable>,
) : Exception() {
    override val message: String = buildString {
        val size = list.size
        if (size == 1) {
            append(list[0].message)
            return@buildString
        }
        list.forEachIndexed { index, throwable ->
            val current = index + 1
            append("[Exception ${index + 1}/$size] ${throwable.message}")
            if (current != size) append("\n\n")
        }
    }.trimEnd()
}

fun knitInternalError(message: String): Nothing = throw KnitInternalError(message)

data class KnitInternalError(override val message: String) :
    IllegalArgumentException("$message \n$internalErrorMessage")

fun illegalState(message: String): Nothing {
    throw IllegalStateException(message)
}

private fun StringBuilder.injectionLog(injection: Injection) {
    append(injection.from.name)
    append(' ')
    append(injection.providesMethod.logName)
}

class KnitSimpleError(
    override val message: String, override val cause: Throwable? = null
) : RuntimeException(message, cause)
