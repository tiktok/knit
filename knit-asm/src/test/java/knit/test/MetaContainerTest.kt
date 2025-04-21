package knit.test

import knit.Component
import knit.Provides
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.assertKnitInternalError
import knit.test.base.knitTypeOf
import knit.test.base.readComponents
import knit.test.base.readContainer
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/5
 * @author yuejunyu.0
 */
@TestTargetClass(MetadataContainer::class)
class MetaContainerTest : KnitTestCase {
    interface InterfaceComponent {
        @Component
        val component: Any
    }

    interface InterfaceComponentSuccess {
        @get:Component
        val component: Any
    }

    @Test
    fun `test Component annotation for interface`() {
        assertKnitInternalError(
            "knit get method [getComponent\$annotations()V] from ${InterfaceComponent::class.internalName} failed!" +
                " In some cases, it is because of you are uses @Component at property in abstract class, change to @get:Component to avoid this problem.",
        ) {
            readContainer<InterfaceComponent>().toContext()
        }
        readContainer<InterfaceComponentSuccess>().toContext()
    }

    interface InterfaceProvides {
        @get:Provides
        val foo: Any
    }

    @Test
    fun `test Provides annotation for interface`() {
        val (component) = readComponents(InterfaceProvides::class)
        val single = component.provides.single()
        Assertions.assertEquals(knitTypeOf<Any>(), single.providesTypes.single())
    }

    @Component
    class PrivatePropertyComponent {
        @Provides
        private val provideString = "foo"
    }

    @Test
    fun `test read property error`() {
        val container = readContainer<PrivatePropertyComponent>()
        val e = assertThrows<IllegalArgumentException> {
            container.toContext()
        }
        val expected = "property: [provideString] in ${PrivatePropertyComponent::class.internalName} " +
            "must have a getter function, please **not make** property is `private` " +
            "or not add `@JvmField` on it " +
            "or use method with @Provides instead of provide a property."
        Assertions.assertEquals(
            expected, e.message,
        )
    }
}
