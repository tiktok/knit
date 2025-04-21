package knit.test

import knit.Component
import knit.Provides
import knit.Singleton
import knit.test.base.AlwaysFailedIdMapper
import knit.test.base.TestTargetClass
import knit.test.base.knitTypeOf
import knit.test.base.readContainer
import knit.test.base.readContainers2
import knit.test.base.toComponent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/4
 * @author yuejunyu.0
 */
@TestTargetClass(ComponentClass::class)
class ComponentClassTest {
    @Component
    class TestCustomConstructor {
        @Provides
        constructor(a: String)

        @Singleton
        @Provides
        constructor(a: String, b: String)
    }

    @Provides
    class NoPublicMainConstructor private constructor()

    @Component
    class NoPublicConstructor private constructor() {
        @Provides
        private constructor(a: String) : this()
    }

    @Component
    class NullableComposite(
        @Component private val parent: String?
    )

    @Test
    fun `test custom constructor`() {
        val (container) = readContainer<TestCustomConstructor>()
        val component = container.toComponent()
        val provides = component.provides

        val type = knitTypeOf<TestCustomConstructor>()
        val provides0 = ProvidesMethod.fromConstructor(
            container, type, AlwaysFailedIdMapper,
            container.constructors[0],
            container.node.methods[0],
            null,
        )

        val provides1 = ProvidesMethod.fromConstructor(
            container, type, AlwaysFailedIdMapper,
            container.constructors[1],
            container.node.methods[1],
            null,
        )

        Assertions.assertEquals(provides0, provides[0])
        Assertions.assertEquals(provides1, provides[1])
    }

    @Test
    fun `test failed when no main constructor`() {
        val (noMainConstructor, noConstructor) = readContainers2<NoPublicMainConstructor, NoPublicConstructor>()
        val eMain = assertThrows<IllegalStateException> {
            noMainConstructor.toComponent()
        }
        val eNormal = assertThrows<IllegalArgumentException> {
            noConstructor.toComponent()
        }
        Assertions.assertEquals(
            "no main constructor for ${NoPublicMainConstructor::class.internalName} or this is a private constructor.",
            eMain.message,
        )
        Assertions.assertEquals(
            "<init>(Ljava/lang/String;)V in ${NoPublicConstructor::class.internalName} must not be private.",
            eNormal.message,
        )
    }

    @Test
    fun `test nullable cannot used for composite component property`() {
        val container = readContainer<NullableComposite>()[0]
        val e = assertThrows<IllegalArgumentException> {
            container.toComponent()
        }
        Assertions.assertEquals(
            "component parent in ${NullableComposite::class.internalName} cannot nullable",
            e.message,
        )
    }

    interface FooInterface {
        @Provides
        @Singleton
        fun providesString()
    }

    @Test
    fun `interface couldn't has any @Singleton`() {
        val container = readContainer<FooInterface>()[0]
        val e = assertThrows<IllegalStateException> {
            container.toComponent()
        }
        Assertions.assertEquals(
            "interface [${FooInterface::class.internalName}] couldn't has any @Singleton, current singletons: [${FooInterface::providesString.name}]",
            e.message,
        )
    }
}
