//  Copyright (c) 2026 by TikTok Ltd., All rights reserved.
//  Licensed under the Apache License Version 2.0 that can be found in the
//  LICENSE file in the root directory of this source tree.

package knit.test.bugfix

import knit.Factory
import knit.IntoList
import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by junyu on 2026/4/2
 * @author yuejunyu.0@tiktok.com
 */
class MultiFactoryInjectFix : KnitTestCase {
    @KnitMock
    interface Target

    @KnitMock
    @IntoList
    @Provides(Target::class)
    class Target1 : Target

    @KnitMock
    @IntoList
    @Provides(Target::class)
    class Target2 : Target

    @KnitMock
    class Container {
        val targets: List<Factory<Target>> by di
    }

    @Test
    fun testFactoryListInject() {
        val targets = Container().targets.map { it() }
        Assertions.assertEquals(targets.size, 2)
    }
}