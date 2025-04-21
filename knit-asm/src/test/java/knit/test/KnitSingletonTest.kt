package knit.test

import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.knitTypeOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.element.KnitSingleton

/**
 * Created by yuejunyu on 2023/7/5
 * @author yuejunyu.0
 */
@TestTargetClass(KnitSingleton::class)
class KnitSingletonTest : KnitTestCase {
    @Test
    fun `test non global constructor`() {
        val type = knitTypeOf<Any>()
        val e = assertThrows<IllegalArgumentException> {
            KnitSingleton.from(false, "<init>", "()V", type, false)
        }
        Assertions.assertEquals(
            "Singleton for constructor must not be a inner class: $type",
            e.message,
        )
    }
}