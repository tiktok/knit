package knit.test

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.TestTargetClass
import knit.test.base.new
import knit.test.base.readContainers3
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.writer.ComponentWriter

/**
 * Created by liangyingwen on 2023/8/16
 * @author liangyingwen
 */
@TestTargetClass(ComponentWriter::class)
class ParentClassProvideComponentTest {

    @Component
    class ParentComponent {
        @Provides
        val str: String = "inject-str"
    }

    @Component
    class ChildComponent : AbstractChild() {
        val inject: String by di
    }

    open class AbstractChild {
        @Component
        val parent: ParentComponent = ParentComponent()
    }

    @Test
    fun `test parent provide component write`() {
        val context = readContainers3<ChildComponent, AbstractChild, ParentComponent>().toContext()
        val loader = context.toClassLoader()
        val child = loader.new<ChildComponent>()
        val injectStr = child["getInject"]().obj
        Assertions.assertEquals("inject-str", injectStr)
    }
}