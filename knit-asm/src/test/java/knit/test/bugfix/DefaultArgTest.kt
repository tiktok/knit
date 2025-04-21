package knit.test.bugfix

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by yuejunyu on 2024/2/29
 * @author yuejunyu.0
 */
class DefaultArgTest : KnitTestCase {
    @KnitMock
    class MultiCtorSample {
        val from: String

        @Provides
        constructor() {
            from = "default"
        }

        @Provides
        constructor(from: String) {
            this.from = from
        }
    }

    @KnitMock
    @Component
    class Container {
        val multiCtorSample: MultiCtorSample by di
    }

    @Test
    fun `test inject with multi constructors`() {
        val container = Container()
        val sampleFrom = container.multiCtorSample.from
        Assertions.assertEquals("default", sampleFrom)
    }
}