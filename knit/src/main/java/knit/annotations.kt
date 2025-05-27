// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package knit

import kotlin.reflect.KClass

/**
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Component

@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.BINARY)
annotation class Provides(
    vararg val parents: KClass<*>,
)

/**
 * Mark elements as a Singleton, Backing fields are used behind to store singleton variables, and they are created on the first call.
 *
 * @property threadsafe Double null check is used at implement locking for threadsafe.
 * @property isSingleton for property injection, singleton is the default logic, but you can set
 *     it to false if you want to get this object everytime without cache
 */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.BINARY)
annotation class Singleton(
    val threadsafe: Boolean = true,
    val isSingleton: Boolean = true,
)

@Target(
    AnnotationTarget.CLASS, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
annotation class Named(
    val value: String = "",
    val qualifier: KClass<*> = Any::class,
)

/**
 * it's like [Provides], but it is a special type for Android ViewModel,
 * just because android must use custom delegates to generate vm.
 * @property parents same as [Provides.parents]
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
annotation class KnitViewModel(
    vararg val parents: KClass<*>,
)

/**
 * Sometimes, you may don't want to let knit inject into this field.
 *
 * e.g. Knit will treat the ViewModel should be injected if it is delegated by ViewModel delegation method
 * and the ViewModel class is annotated with [KnitViewModel], but maybe you already initialized it from
 * other callsites, so this injection can be ignored if needed by using [IgnoreInjection] annotation
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class IgnoreInjection

// collection provides start
/**
 * **Note:** using List does not guarantee order, it just allows repeating elements
 * when comparing with [IntoSet]
 *
 * @param onlyCollectionProvides Set to true if **only** want to provide it into a collection.
 *  set to false if you also want to provide this object as a normal @[Provides], rather
 *  than exclusively into a collection.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY,
)
annotation class IntoList(val onlyCollectionProvides: Boolean = true)

/**
 * @param onlyCollectionProvides Set to true if **only** want to provide it into a collection.
 *  set to false if you also want to provide this object as a normal @[Provides], rather
 *  than exclusively into a collection.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY,
)
annotation class IntoSet(val onlyCollectionProvides: Boolean = true)

/**
 * @param onlyCollectionProvides Set to true if **only** want to provide it into a collection.
 *  set to false if you also want to provide this object as a normal @[Provides], rather
 *  than exclusively into a collection.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY,
)
annotation class IntoMap(val onlyCollectionProvides: Boolean = true)
// collection provides end

/**
 * Constraints priority of type providers, high priority provider will be matched first.
 * All provider's priority is 0 by default.
 */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.BINARY)
@KnitExperimental
annotation class Priority(
    val priority: Int = 0,
)
