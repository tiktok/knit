package knit.test

import knit.Component
import knit.KnitExperimental
import knit.Provides
import knit.Singleton
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by yuejunyu on 2023/10/24
 * @author yuejunyu.0
 */
class SingletonTest : KnitTestCase {
    @KnitMock
    @Component
    class Target {
        private var i = 0

        @Provides
        val s get() = "s${i++}"

        @Singleton(isSingleton = false)
        val ds: String by di // dynamic string :)

        @Singleton(isSingleton = true)
        val ss: String by di // stable string :)

        @KnitExperimental
        val dsByMut: String by di()
    }

    @Test
    @KnitExperimental
    fun `test non-singleton val`() {
        val target = Target()
        Assertions.assertEquals("s0", target.ss)
        Assertions.assertEquals("s1", target.ds)
        Assertions.assertEquals("s2", target.ds)
        Assertions.assertEquals("s0", target.ss)

        Assertions.assertEquals("s3", target.dsByMut)
        Assertions.assertEquals("s4", target.dsByMut)
    }
}