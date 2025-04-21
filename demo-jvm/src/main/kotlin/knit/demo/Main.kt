// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package knit.demo

import knit.Provides
import knit.di

/**
 * Created by yuejunyu on 2025/4/18
 * @author yuejunyu.0
 */
fun main() {
    SimpleClass().run()
}

class SimpleClass {
    @Provides
    private fun giveStr(): String = "Hello Knit!"

    private val injected: String by di

    fun run() {
        println(injected)
    }
}
