//  Copyright (c) 2026 by TikTok Ltd., All rights reserved.
//  Licensed under the Apache License Version 2.0 that can be found in the
//  LICENSE file in the root directory of this source tree.

package knit.test.bugfix

import knit.Provides
import knit.di
import knit.internal.GlobalProvides
import knit.test.base.KnitTestCase
import knit.test.base.readContainers
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.CircularDependencyException
import tiktok.knit.plugin.fqn

/**
 * Created by junyu on 2026/3/11
 * @author yuejunyu.0@tiktok.com
 */
class ErrWhenCircularProvides : KnitTestCase {
    class Container {
        @Provides
        private val bar: Bar by di

        @Provides
        private val foo: Foo by di
    }

    @Provides
    class Foo(
        private val bar: Bar
    )

    @Provides
    class Bar(
        private val foo: Foo,
    )

    @Test
    fun `inject circular provides`() {
        val containers = readContainers(Container::class, Foo::class, Bar::class, GlobalProvides::class)
        val e = assertThrows<CircularDependencyException> {
            containers.toContext().toClassLoader()
        }
        Assertions.assertEquals(e.cycle.size, 3)
        Assertions.assertEquals(e.componentName, Container::class.fqn)
        e.printStackTrace()
    }
}