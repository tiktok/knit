// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

@file:Suppress("FunctionName", "NOTHING_TO_INLINE")

package knit

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 *
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */

/**
 * Mark a variable as knit injection consumer.
 *
 * Value will be initialized when access it at the first time and **never changed again**.
 *
 * ```kotlin
 * var foo = 0
 *
 * @Component
 * class Sample {
 *     @Provides fun fooProvider() = ++foo
 *     val bar: Int by di
 *
 *     fun execute() {
 *         println(bar) // 1
 *         println(bar) // 1
 *         println(bar) // 1
 *     }
 * }
 * ```
 */
inline val di: DIStubImpl get() = DIStubImpl

/**
 * Mark a variable as knit mutable injection consumer.
 *
 * It will **call the producer everytime** you access this variable. Not same with the property injection consumer.
 *
 * ```kotlin
 * var foo = 0
 *
 * @Component
 * class Sample {
 *     @Provides fun fooProvider() = ++foo
 *     val bar: Int by di()
 *
 *     fun execute() {
 *         println(bar) // 1
 *         println(bar) // 2
 *         println(bar) // 3
 *     }
 * }
 * ```
 */
@KnitExperimental
inline fun di(): DIGetterStubImpl = DIGetterStubImpl

/** mutable delegate implementation stub for di */
object DIGetterStubImpl : AbsDIStub()

/** immutable delegate implementation stub for di */
object DIStubImpl : AbsDIStub()

/** common delegate implementation stub */
abstract class AbsDIStub : ReadOnlyProperty<Any, Nothing> {
    override operator fun getValue(thisRef: Any, property: KProperty<*>): Nothing {
        throw knitStubImplementationError(thisRef, property)
    }

    // static injection impl
    // e.g. file level variable injections.
    operator fun <T> getValue(thisRef: Nothing?, property: KProperty<*>): T {
        throw knitStubImplementationError(thisRef, property)
    }
}

private fun knitStubImplementationError(thisRef: Any?, property: KProperty<*>) = NotImplementedError(
    "this property(${property.name}) implemented through Knit in bytecode, " +
        "if you meet this problem, please check if you have add @Component annotations to your " +
        "inject container(${thisRef?.javaClass?.name}) correctly.",
)

typealias Factory<R> = () -> R

/** provide something as [kotlin.Lazy] using [Factory] */
@Provides
@PublishedApi
@Suppress("unused")
internal fun <T> provideLazy(provider: Factory<T>): Lazy<T> = lazy(provider)

/**
 * optional injected value.
 *
 * It is different with nullable type, because [Option] focused on no injection, not means nullable type.
 * Here is an example:
 *
 * ```kotlin
 * class Sample {
 *   @Provides val foo = Foo()
 *   val fooConsumer: Foo? by di // cannot be injected! `Foo?` and `Foo` are not same type!
 *
 *   @Provides val bar = Bar()
 *   val barConsumer: Option<Bar> by di // can be injected.
 *
 *   val optSb: Option<StringBuilder> by di // can be injected by `Option` 0-arg constructor.
 *   val nullableSb: StringBuilder? by di // cannot be injected because no `StringBuilder?` provider.
 * }
 * ```
 */
class Option<out T> : ReadOnlyProperty<Any, T?> {
    @Provides
    constructor() {
        innerValue = UnInitObject
    }

    @OptIn(KnitExperimental::class)
    @Provides
    @Priority(1)
    constructor(value: T) {
        innerValue = value
    }

    /** returns the real value as nullable */
    @Suppress("UNCHECKED_CAST")
    inline val asNullable: T? get() = innerValue.takeUnless { it == UnInitObject } as T?

    inline val unwrap: T
        get() {
            val unInit = innerValue == UnInitObject
            if (unInit) throw IllegalArgumentException("try unwrap non-existed value from Knit Option.")
            @Suppress("UNCHECKED_CAST")
            return innerValue as T
        }

    /** take [Option] as a delegation of nullable property */
    override fun getValue(thisRef: Any, property: KProperty<*>): T? = asNullable

    @PublishedApi
    internal val innerValue: Any?

    @PublishedApi
    internal object UnInitObject

    companion object {
        /** no injected value for all types. */
        val None: Option<Nothing> = Option()

        /** an option which contains a certain value */
        @JvmStatic
        fun <T> Ok(value: T) = Option(value)
    }
}

