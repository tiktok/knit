package knit.test

import knit.Provides
import knit.test.base.TestTargetClass
import knit.test.base.readContainer
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/24
 * @author yuejunyu.0
 */
@TestTargetClass(ProvidesMethod::class)
class ProvidesMethodTest {
    @Provides
    private class TestStaticProvidesCase

    interface TestSuspendCase {
        @Provides
        suspend fun getString(): String
    }

    @Test
    fun `test failed provides`() {
        val suspendE = assertThrows<IllegalArgumentException> {
            readContainer<TestSuspendCase>().toContext()
        }
        Assertions.assertEquals(
            "suspend is not supported for injection: ${TestSuspendCase::class.internalName}.getString",
            suspendE.message,
        )
        val staticE = assertThrows<IllegalArgumentException> {
            readContainer<TestStaticProvidesCase>().toContext()
        }
        Assertions.assertEquals(
            "static @Provides element must be public: ${TestStaticProvidesCase::class.internalName}.<init>",
            staticE.message,
        )
    }
}