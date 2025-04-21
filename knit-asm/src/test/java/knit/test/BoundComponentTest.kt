package knit.test

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.asTestBound
import knit.test.base.assertContentMatches
import knit.test.base.assertKnitInternalError
import knit.test.base.new
import knit.test.base.readComponents
import knit.test.base.readContainers
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.injection.CPF
import tiktok.knit.plugin.injection.method
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/5
 * @author yuejunyu.0
 */
@TestTargetClass(BoundComponentClass::class)
class BoundComponentTest : KnitTestCase {
    @Component
    class CannotFoundSample {
        @Component
        val a: StringBuilder = StringBuilder()
    }

    interface ParentContainer {
        @Provides
        fun provideS(): String
    }

    @Component
    class ChildContainer : ParentContainer {
        override fun provideS(): String = ""

        @Provides
        fun provideSS(): String = ""
    }

    @Component
    class ChildOverrideContainer : ParentContainer {
        override fun provideS(): String = "foo"
    }

    @Component
    class ChildOverrideInjected(
        @Component private val container: ParentContainer
    ) {
        val str: String by di
    }

    @Test
    fun `test cannot find a way`() {
        val (component) = readComponents(CannotFoundSample::class).asTestBound()
        val required = BoundComponentTest::class.internalName
        val from = CannotFoundSample::class.internalName
        assertKnitInternalError("cannot found a way to acquire $required from $from") {
            component.findWay(required)
        }
    }

    @Test
    fun `test provides by impl`() {
        val (component, parentComponent) = readComponents(
            ChildContainer::class, ParentContainer::class,
        ).asTestBound()
        val allProvides = CPF.all(component, true).map { it.method }
        assertContentMatches(component.provides + parentComponent.provides, allProvides)
    }

    @Test
    fun `test provides something by its parent`() {
        val classloader = readContainers(
            ParentContainer::class, ChildOverrideContainer::class, ChildOverrideInjected::class,
        ).toContext().toClassLoader()
        val child = classloader.new<ChildOverrideContainer>()
        val injected = classloader.new<ChildOverrideInjected>(child.obj)
        val output = injected["getStr"]().obj
        Assertions.assertEquals("foo", output)
    }
}
