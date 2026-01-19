package knit.test.bugfix

import knit.Provides
import knit.Singleton
import knit.di
import knit.internal.GlobalProvides
import knit.test.base.new
import knit.test.base.readContainers
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by junyu on 2026/1/19
 * @author yuejunyu.0@tiktok.com
 */
class GlobalUseAnyTypeTest {
    class GlobalTestContainer {
        val ctorInjected: GlobalCtorTarget by di
        val funcInjected: GlobalFuncTarget by di
        val ctorInjected2: GlobalCtorTarget by di
        val funcInjected2: GlobalFuncTarget by di

        fun test() {
            Assertions.assertNotEquals(ctorInjected, ctorInjected2)
            Assertions.assertEquals(funcInjected, funcInjected2)
            Assertions.assertEquals(ctorInjected.num, 2L)
            Assertions.assertEquals(funcInjected.num, 5)
        }
    }

    object GlobalStaticProvides {
        @JvmStatic
        @Provides
        fun providesLong(): Long = 2L

        @JvmStatic
        @Provides
        fun providesInt(): Int = 5

        @JvmStatic
        @Provides
        @Singleton
        fun globalFunTarget(num: Int, arg: Foo): GlobalFuncTarget {
            return GlobalFuncTarget(num, arg)
        }
    }

    @Provides
    class GlobalCtorTarget(val arg: Foo, val num: Long)

    class GlobalFuncTarget(val num: Int, val arg: Foo)

    @Provides
    class Foo

    @Test
    fun `test global singleton`() {
        val containers = readContainers(
            GlobalTestContainer::class, GlobalProvides::class, GlobalStaticProvides::class,
            GlobalCtorTarget::class, GlobalFuncTarget::class, Foo::class,
        )
        val loader = containers.toContext().toClassLoader()
        val injectTarget = loader.new<GlobalTestContainer>()
        injectTarget["test"]()
    }
}