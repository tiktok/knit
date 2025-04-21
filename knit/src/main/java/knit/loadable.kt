// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package knit

/**
 * Created by yuejunyu on 2023/6/1
 * @author yuejunyu.0
 */
internal object UnloadedValue

@Provides
class Loadable<out T>(private val factory: Factory<out T>) {
    private var innerValue: Any? = UnloadedValue

    /**
     * load value from factory and return it. it will always return a new instance
     */
    fun load(): T {
        val newValue = factory()
        innerValue = newValue
        return newValue
    }

    /**
     * get the cached value and return it
     * @return null if it didn't load or [T] is nullable
     */
    fun get(): T? {
        val innerValue = innerValue
        if (innerValue === UnloadedValue) return null
        @Suppress("UNCHECKED_CAST")
        return innerValue as T
    }

    /**
     * remove the loaded value
     * @return the value before remove.
     */
    fun unload(): T? {
        val oldValue = innerValue
        if (oldValue === UnloadedValue) return null
        else synchronized(this) {
            @Suppress("UNCHECKED_CAST")
            val sOldValue = innerValue as T
            innerValue = null
            return sOldValue.takeUnless { it === UnloadedValue }
        }
    }
}
