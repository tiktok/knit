package knit.test

import knit.di
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.readContainer
import knit.test.base.toComponent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.element.InjectedGetter
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/8
 * @author yuejunyu.0
 */
@TestTargetClass(InjectedGetter::class)
class InjectedGetterTest : KnitTestCase {
    class Container<T> {
        val injected: List<T> by di
    }

    @Test
    fun `fails when inject getter contains type parameter`() {
        val container = readContainer<Container<String>>()[0]
        val e = assertThrows<IllegalArgumentException> {
            container.toComponent()
        }
        val className = Container::class.internalName
        Assertions.assertEquals(
            "in $className, we disallow to inject a type which contains type parameter. " +
                "functionName: getInjected, type: ${List::class.fqn}<T0>",
            e.message,
        )
    }
}