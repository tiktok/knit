package knit.test.bugfix

import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Test

/**
 * Created by junyu on 2025/5/20
 * @author yuejunyu.0@tiktok.com
 */
class InheritProvidedTest : KnitTestCase {
    @KnitMock
    @Provides
    class Yichen

    @KnitMock
    interface ParentProvided {
        @get:Provides
        val s: Yichen
    }

    @KnitMock
    class ChildComponent : ParentProvided {
        override val s: Yichen by di
    }

    @Test
    fun `child by di with parent provided`() {
        ChildComponent().s
    }
}