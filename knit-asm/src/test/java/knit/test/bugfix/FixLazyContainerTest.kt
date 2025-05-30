package knit.test.bugfix

import knit.IntoList
import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Created by junyu on 2025/5/30
 * @author yuejunyu.0@tiktok.com
 */
class FixLazyContainerTest : KnitTestCase {
    @KnitMock
    class LazyContainer {
        @Provides
        val s: String = "abc"

        val consumeLazy: Lazy<String> by di
    }

    @KnitMock
    class ListLazyContainer {
        @Provides
        val s: List<String> = listOf("abd")

        val consumeLazy: Lazy<List<String>> by di
    }

    @KnitMock
    class MultiProvidesLazyContainer {
        @Provides
        @IntoList
        val s: String = "abd"

        val consumeLazy: Lazy<List<String>> by di
    }

    @Test
    fun doTest() {
        Assertions.assertEquals("abc", LazyContainer().consumeLazy.value)
        val list = listOf("abd")
        Assertions.assertEquals(list, ListLazyContainer().consumeLazy.value)
        Assertions.assertEquals(list, MultiProvidesLazyContainer().consumeLazy.value)
    }
}
