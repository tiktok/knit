// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package knit.internal

/**
 * Created by yuejunyu on 2023/6/2
 * @author yuejunyu.0
 */
object GlobalProvides

object MultiBindingBuilder {
    @JvmStatic
    fun list(array: Array<*>): List<*> {
        return array.toList()
    }

    @JvmStatic
    fun set(array: Array<*>): Set<*> {
        return array.toSet()
    }

    @JvmStatic
    fun map(arrayOfPair: Array<*>): Map<*, *> {
        val map = HashMap<Any?, Any?>()
        for (any in arrayOfPair) {
            any as Pair<*, *>
            map[any.first] = any.second
        }
        return map
    }
}

