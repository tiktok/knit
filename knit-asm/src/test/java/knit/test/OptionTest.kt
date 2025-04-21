package knit.test

import knit.Component
import knit.Option
import knit.Provides
import knit.di
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Created by yuejunyu on 2024/3/1
 * @author yuejunyu.0
 */
@TestTargetClass(Option::class)
class OptionTest : KnitTestCase {
    @KnitMock
    @Provides
    class Foo(
        val string: Option<String>,
        val sb: Option<StringBuilder>,
    )

    @KnitMock
    @Component
    class Container {
        @Provides
        val string = "inject to foo"
        val foo: Foo by di
    }

    @Test
    fun `test option`() {
        val container = Container()
        Assertions.assertEquals("inject to foo", container.foo.string.unwrap)
        Assertions.assertNull(container.foo.sb.asNullable)
    }

    @KnitMock
    @Component
    class InjectFailsContainer {
        private val sample: Option<ByteArray> by di
        val delegate: ByteArray? by sample
        fun unwrapTest() = sample.unwrap
    }

    @Test
    fun `test injection fails`() {
        val failsContainer = InjectFailsContainer()
        Assertions.assertNull(failsContainer.delegate)
        val exception = assertThrows<IllegalArgumentException> {
            failsContainer.unwrapTest()
        }
        Assertions.assertEquals("try unwrap non-existed value from Knit Option.", exception.message)
    }

    @Test
    fun `test none and ok`() {
        val none: Option<String> = Option.None
        val ok: Option<String> = Option.Ok("foo")
        Assertions.assertNull(none.asNullable)
        Assertions.assertEquals("foo", ok.unwrap)
    }
}
