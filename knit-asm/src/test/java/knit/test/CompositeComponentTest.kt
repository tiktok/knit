package knit.test

import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.knitTypeOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.element.CompositeComponent

/**
 * Created by yuejunyu on 2023/7/4
 * @author yuejunyu.0
 */
@TestTargetClass(CompositeComponent::class)
class CompositeComponentTest : KnitTestCase {
    @Test
    fun `test success composite component`() {
        val type = knitTypeOf<MutableList<List<String>>>()
        CompositeComponent(type)
    }

    @Test
    fun `test failed composite component`() {
        val type = knitTypeOf<MutableList<in String>>()
        val e = assertThrows<IllegalArgumentException> {
            CompositeComponent(type)
        }
        Assertions.assertEquals("composite component must not includes variance", e.message)
    }
}