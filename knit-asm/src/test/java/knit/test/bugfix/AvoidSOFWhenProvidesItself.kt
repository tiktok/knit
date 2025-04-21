package knit.test.bugfix

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by yuejunyu on 2024/1/16
 * @author yuejunyu.0
 */
class AvoidSOFWhenProvidesItself : KnitTestCase {
    @KnitMock
    @Component
    class SimpleStringProvider {
        @Provides
        val stringRealProvider: String = "aaa"

        @Provides
        val originInt: Int = 1
    }

    @KnitMock
    @Component
    class SimpleCase {
        @Component
        private val provider: SimpleStringProvider = SimpleStringProvider()

        @Provides
        val providesString: String by di

        @Provides
        fun injectItSelfAsRequirement(requirement: Int): Int {
            return requirement + 3
        }

        val intVerifier: Int by di
    }


    @Test
    fun `inject provided by itself`() {
        val simpleCase = SimpleCase()
        Assertions.assertEquals("aaa", simpleCase.providesString)
        Assertions.assertEquals(4, simpleCase.intVerifier)
    }
}