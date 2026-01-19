// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import knit.Component
import knit.DIGetterStubImpl
import knit.DIStubImpl
import knit.Factory
import knit.IgnoreInjection
import knit.IntoList
import knit.IntoMap
import knit.IntoSet
import knit.KnitExperimental
import knit.KnitViewModel
import knit.Named
import knit.Priority
import knit.Provides
import knit.Singleton
import knit.internal.GlobalProvides
import knit.internal.MultiBindingBuilder
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.injection.Injection
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference
import kotlin.metadata.ClassName
import kotlin.metadata.KmClassifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaGetter

/**
 * Created by yuejunyu on 2023/6/5
 * @author yuejunyu.0
 */
val ClassNode.allAnnotations
    get() = invisibleAnnotations.orEmpty() + visibleAnnotations.orEmpty()

val MethodNode.allAnnotations
    get() = invisibleAnnotations.orEmpty() + visibleAnnotations.orEmpty()

val function0Desc: DescName = Factory::class.descName
val objectInternalName = Any::class.internalName
val objectType: Type = Type.getType(Any::class.java)

val componentDesc: DescName = Component::class.descName
val providesDesc: DescName = Provides::class.descName
val singletonDesc: DescName = Singleton::class.descName
val namedDesc: DescName = Named::class.descName
val namedInternalName: InternalName = Named::class.internalName

@OptIn(KnitExperimental::class)
val priorityDesc: DescName = Priority::class.descName

val intoListDesc: DescName = IntoList::class.descName
val intoSetDesc: DescName = IntoSet::class.descName
val intoMapDesc: DescName = IntoMap::class.descName

val diStubDesc: DescName = DIStubImpl::class.descName
val diMutStubDesc: DescName = DIGetterStubImpl::class.descName
val knitVMLazyDesc: DescName = "Lknit/android/internal/KnitVMLazy;"
val globalProvidesInternalName: InternalName = GlobalProvides::class.internalName
val mbBuilderInternalName: InternalName = MultiBindingBuilder::class.internalName

val listDesc: DescName = List::class.descName
val setDesc: DescName = Set::class.descName
val mapDesc: DescName = Map::class.descName

val pairClassifier = KnitClassifier(Pair::class.descName)

const val internalErrorMessage =
    "Maybe this is a knit internal error, if you have some questions, " +
        "pls report to knit framework developer yuejunyu.0"

interface InheritJudgement {
    operator fun invoke(thisClassDesc: DescName, parentClassDesc: DescName): Boolean {
        return inherit(
            Type.getType(thisClassDesc).internalName,
            Type.getType(parentClassDesc).internalName,
        )
    }

    fun inherit(thisName: InternalName, parentName: InternalName): Boolean

    object AlwaysFalse : InheritJudgement {
        override fun inherit(thisName: InternalName, parentName: InternalName): Boolean {
            return false
        }

        override fun invoke(thisClassDesc: DescName, parentClassDesc: DescName): Boolean {
            return false
        }
    }
}

inline val KProperty<*>.callName get() = javaGetter?.name

inline fun AnnotationNode.onEach(action: (attrName: String, value: Any) -> Unit) {
    var curIndex = 0
    values ?: return
    while (curIndex < values.size) {
        val name = values[curIndex] as String
        val value = values[curIndex + 1]
        action(name, value)
        curIndex += 2
    }
}

/** same with [ClassName], like `java/lang/Object$XXX` */
typealias InternalName = String

/** qualified name, path dash will replace with dot */
typealias QualifiedName = String

/** it means this is a jvm descriptor, like: `Ljava/lang/Object$XXX;` */
typealias DescName = String

/** represent a function name */
typealias FuncName = String

/** property access name, maybe it can be a getter function or direct access field */
typealias PropAccName = String

private const val PROP_SUFFIX = "_\$knitProp"

fun PropAccName.isGetter(): Boolean {
    return !endsWith(PROP_SUFFIX)
}

fun PropAccName.getFieldName(): String {
    return removeSuffix(PROP_SUFFIX)
}

fun PropAccName.printable() = getFieldName()

/** @receiver field name */
fun String.toFieldAccess(): PropAccName {
    return this + PROP_SUFFIX
}

/** function descriptor, like: `(Ljava/lang/Object;Ljava/lang/String;)V`*/
typealias FuncDesc = String

fun DescName.toInternalName(): InternalName {
    return Type.getType(this).internalName
}

fun InternalName.toObjDescName(): DescName {
    return "L$this;"
}

val InternalName.fqn: QualifiedName get() = Type.getObjectType(this).className

inline val KClass<*>.descName: DescName get() = Type.getDescriptor(java)
inline val KClass<*>.internalName: InternalName get() = Type.getInternalName(java)
inline val KClass<*>.fqn: QualifiedName get() = Type.getType(java).className

typealias Opcode = Int

@Suppress("NOTHING_TO_INLINE")
inline operator fun Opcode.invoke(accessCode: Int): Boolean {
    return (accessCode and this) != 0
}

object Logger {
    var delegate: ILogger? = null
    private const val TAG = "Knit"
    fun i(information: String) {
        delegate?.i(TAG, information)
    }

    fun w(warning: String, t: Throwable?) {
        delegate?.w(TAG, warning, t)
    }

    fun e(error: String, t: Throwable?) {
        delegate?.e(TAG, error, t)
    }
}

interface ILogger {
    fun i(tag: String, information: String)
    fun w(tag: String, warning: String, t: Throwable?)
    fun e(tag: String, error: String, t: Throwable?)
}

inline val MethodNode.isStatic: Boolean get() = Opcodes.ACC_STATIC(access)
inline val MethodNode.isPublic: Boolean get() = Opcodes.ACC_PUBLIC(access)
inline val ClassNode.isPublic: Boolean get() = Opcodes.ACC_PUBLIC(access)

fun List<AnnotationNode>.annotated(desc: DescName): Boolean {
    return any { it.desc == desc }
}

fun List<AnnotationNode>.firstOrNull(desc: DescName): AnnotationNode? {
    return firstOrNull { it.desc == desc }
}

@OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)
inline fun <T> buildListCompat(@BuilderInference action: MutableList<T>.() -> Unit): List<T> {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return mutableListOf<T>().apply(action)
}

val basicTypeWrappers = arrayOf(
    "java/lang/Integer",
    "java/lang/Byte",
    "java/lang/Short",
    "java/lang/Long",
    "java/lang/Float",
    "java/lang/Double",
    "java/lang/Character",
    "java/lang/Boolean",
)

val basicTypeWrapperDesc = basicTypeWrappers.map { "L$it;" }.toTypedArray()

val basicTypes = arrayOf(
    "I", // int
    "B", // byte
    "S", // short
    "J", // long
    "F", // float
    "D", // double
    "C", // char
    "Z",  // boolean
)

val unboxFunctions = arrayOf(
    "intValue",   // java.lang.Integer
    "byteValue",  // java.lang.Byte
    "shortValue", // java.lang.Short
    "longValue",  // java.lang.Long
    "floatValue", // java.lang.Float
    "doubleValue", // java.lang.Double
    "charValue",  // java.lang.Character
    "booleanValue", // java.lang.Boolean
)

const val knitVmFactoryOwnerName: InternalName = "androidx/lifecycle/HasDefaultViewModelProviderFactory"
const val knitVmFactoryImplName: InternalName = "knit/android/internal/VMPFactoryImpl"
val knitVMAnnotationDesc = KnitViewModel::class.descName

val ignoreInjectionDesc = IgnoreInjection::class.descName

fun KmClassifier.internalName(): InternalName? {
    return when (this) {
        is KmClassifier.Class -> name
        is KmClassifier.TypeAlias -> name
        is KmClassifier.TypeParameter -> null
    }
}

enum class MultiBindingType(val functionName: String, kClass: KClass<*>) {
    L("list", List::class),
    S("set", Set::class),
    M("map", Map::class),
    ;

    val type = KnitType.from(kClass.internalName)
}

fun <T : Any> List<Result<T>>.filterSuccess() = mapNotNull { it.getOrNull() }

inline val Result<Injection>.exception get() = requireNotNull(exceptionOrNull())
inline val Injection.success get() = Result.success(this)

fun List<Result<Injection>>.combine(): Result<List<Injection>> {
    val failed = filter { it.isFailure }
    if (failed.isEmpty()) return Result.success(map { it.getOrThrow() })
    if (failed.size == 1) return Result.failure(failed[0].exception)
    val exception = CombinedInjectionException(failed.map { it.exception })
    return Result.failure(exception)
}

fun <T> List<Result<T>>.partition(): Pair<List<T>, List<Throwable>> {
    val (successResults, failedResults) = partition { it.isSuccess }
    val values = successResults.map { it.getOrThrow() }
    val throwables = failedResults.mapNotNull { it.exceptionOrNull() }
    return values to throwables
}

inline fun List<Result<Injection>>.exactSingleInjection(
    component: BoundComponentClass, requiredType: KnitType,
    allProvides: () -> List<ProvidesMethod>,
): Result<Injection> = exactSingleInjection(
    component, requiredType,
    onEmpty = { null },
    onTypeConflict = { null },
    allProvides = allProvides,
)

inline fun List<Result<Injection>>.exactSingleInjection(
    component: BoundComponentClass,
    requiredType: KnitType,
    onEmpty: () -> Exception?,
    onTypeConflict: (firstInjectionFrom: Injection.From) -> Exception?,
    allProvides: () -> List<ProvidesMethod>,
): Result<Injection> {
    val (injections, exceptions) = partition()
    // return as normal injection results if injection is not empty
    if (injections.isNotEmpty() || exceptions.isEmpty()) return injections.exactSingleInjection(
        component, requiredType, onEmpty, onTypeConflict, allProvides,
    )
    // print merged exceptions
    val throwable = if (exceptions.size == 1) exceptions[0] else CombinedInjectionException(exceptions)
    return Result.failure(throwable)
}

@JvmName("exactSingleInjection_normal")
inline fun List<Injection>.exactSingleInjection(
    component: BoundComponentClass, requiredType: KnitType,
    onEmpty: () -> Exception?,
    onTypeConflict: (firstInjectionFrom: Injection.From) -> Exception?,
    allProvides: () -> List<ProvidesMethod>,
): Result<Injection> {
    val componentName = component.internalName
    if (isEmpty()) {
        val exception = onEmpty() ?: NoProvidesFoundException(
            componentName, requiredType, allProvides(),
        )
        return Result.failure(exception)
    }
    if (size == 1) return Result.success(first())

    // sort by `from`
    val sorted = sortedBy { it.from }
    val first = sorted[0]
    val firstFrom = first.from
    val second = sorted[1]
    if (firstFrom != second.from) {
        // first type not same with second, return first
        return Result.success(first)
    }

    // sort by `priority`
    val sameFromInjections = sorted.filter { it.from == firstFrom }
        .sortedByDescending { it.providesMethod.priority } // high priority will be matched first.
    val highPriorityOne = sameFromInjections[0]
    val lowPriorityOne = sameFromInjections[1]
    if (highPriorityOne.providesMethod.priority > lowPriorityOne.providesMethod.priority) {
        return Result.success(highPriorityOne)
    }

    val e: Exception = onTypeConflict(firstFrom) ?: if (firstFrom == Injection.From.COMPOSITE) {
        TypeConflictInCompositeException(
            component, requiredType, this,
        )
    } else TypeConflictException(
        component.internalName, requiredType, map { it.providesMethod },
    )
    return Result.failure(e)
}

inline fun <R> CompositeComponent.wrapBuildException(content: () -> R): R = try {
    content()
} catch (e: Throwable) {
    throw KnitSimpleError("error occurs when build composite component $type", e)
}
