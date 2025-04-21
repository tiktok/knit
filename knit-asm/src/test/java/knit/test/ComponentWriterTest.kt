package knit.test

import knit.Component
import knit.JFloat
import knit.JInt
import knit.JLong
import knit.Loadable
import knit.Provides
import knit.Singleton
import knit.boxed
import knit.di
import knit.internal.GlobalProvides
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.new
import knit.test.base.readContainer
import knit.test.base.readContainers
import knit.test.base.readContainers4
import knit.test.base.toContext
import knit.test.sample.AccessComponentFromDeps
import knit.test.sample.DeepDepsComponent
import knit.test.sample.DepsComponent
import knit.test.sample.FactoryInjectionObject
import knit.test.sample.FactoryInjectionTarget
import knit.test.sample.GlobalTestObj
import knit.test.sample.InterfaceProviderTarget
import knit.test.sample.StringProvider
import knit.test.sample.StringProviderImpl
import knit.test.sample.TestFullComponent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.writer.ComponentWriter


/**
 * Created by yuejunyu on 2023/6/16
 * @author yuejunyu.0
 */
@TestTargetClass(ComponentWriter::class)
class ComponentWriterTest : KnitTestCase {
    class AttachGenericContainer<T> {
        @Provides
        fun getSet(foo: Map<String, T>): Set<T> = foo.values.toSet()

        @Provides
        val provideMap: Map<String, CharSequence> = emptyMap()

        @Provides
        fun oneThingList(oneThing: T): List<T> = listOf(oneThing)

        @Provides
        fun providesOneThing(): String = "Hello"

        val charSet: Set<CharSequence> by di
        val oneThingList: List<String> by di
    }

    class SimpleContainer {
        val injected: String by di

        @Provides
        @Singleton(false)
        fun provideString(): String {
            return "foo"
        }
    }

    @Test
    fun `test full process`() {
        val containers = readContainers(
            TestFullComponent::class, DepsComponent::class, DeepDepsComponent::class,
        )
        val newLoader = containers.toContext().toClassLoader()
        val depsComponent = newLoader.new<DepsComponent>().obj
        val instance = newLoader.new<TestFullComponent>(depsComponent, "0")
        val resultSb = instance["getSb"]().obj.toString()
        val resultCs = instance["getCs"]().obj.toString()
        println(resultSb)
        println(resultCs)
        Assertions.assertEquals("Hello World!", resultSb)
        Assertions.assertEquals("Hello World! CharSequence~", resultCs)

        val instance2 = newLoader.new<TestFullComponent>(depsComponent, "1")
        val set0 = instance["getSingletonSet"]().obj
        val set1 = instance2["getSingletonSet"]().obj
        Assertions.assertTrue(set0 === set1)
        Assertions.assertEquals(setOf("foo0"), set1)
    }

    object GlobalObjStaticProvides {
        @JvmStatic
        @Provides
        fun provideList(): List<String> = listOf("foo")

        @JvmStatic
        @Provides
        @Singleton
        fun providePair(key: String) = key to "value"
    }

    @Component
    class GlobalTestInjectTarget(
        @Provides val key: String
    ) {
        val obj: GlobalTestObj by di
        val list: List<String> by di
        val pair: Pair<String, String> by di
    }

    @Test
    fun `test global singleton`() {
        val containers = readContainers(
            GlobalTestObj::class, GlobalObjStaticProvides::class,
            GlobalTestInjectTarget::class, GlobalProvides::class,
        )
        val loader = containers.toContext().toClassLoader()
        val injectTarget = loader.new<GlobalTestInjectTarget>("key1")
        val globalTestObj = injectTarget["getObj"]()
        val testString = globalTestObj["getTest"]().obj
        val testList = injectTarget["getList"]().obj
        val testList2 = injectTarget["getList"]().obj
        Assertions.assertEquals("Hello!", testString)
        Assertions.assertEquals(listOf("foo"), testList)
        Assertions.assertTrue(testList === testList2) // property always singleton, it will not re-create

        val key2Target = loader.new<GlobalTestInjectTarget>("key2")
        injectTarget["getPair"]().obj // access first from 1
        val pair = key2Target["getPair"]().obj // and then use it from 2
        Assertions.assertEquals("key1" to "value", pair)
    }

    @Test
    fun `factory injection`() {
        val containers = readContainers(
            FactoryInjectionObject::class, FactoryInjectionTarget::class, GlobalProvides::class,
            Loadable::class,
        )
        val loader = containers.toContext().toClassLoader()
        val injectTarget = loader.new<FactoryInjectionTarget>()
        val injectedObj = injectTarget["getObj"]()
        val helloStr = injectedObj["hello"]().obj
        println(helloStr)
        Assertions.assertEquals("Hello Factory!", helloStr)

        val loadTest = injectTarget["load"]().obj
        Assertions.assertEquals("Hello Factory!", loadTest)
    }

    @Test
    fun `interface injection`() {
        val containers = readContainers(
            InterfaceProviderTarget::class, StringProvider::class, GlobalProvides::class,
            StringProviderImpl::class,
        )
        val loader = containers.toContext().toClassLoader()
        val injectTarget = loader.new<InterfaceProviderTarget>()
        val injectedStr = injectTarget["getStr"]().obj
        Assertions.assertEquals("Interface Provides", injectedStr)
    }

    abstract class AbsStringProvider {
        @Provides
        abstract fun provide(): String
    }

    @Provides(AbsStringProvider::class)
    class AbsStringProviderImpl : AbsStringProvider() {
        override fun provide(): String {
            return "AbsProvides"
        }
    }

    @Component
    class AbsInjected {
        val provide: AbsStringProvider by di
    }

    @Test
    fun `abstract class injection`() {
        val containers = readContainers4<
            AbsStringProvider, AbsStringProviderImpl, GlobalProvides, AbsInjected,
            >()
        val loader = containers.toContext().toClassLoader()
        val injected = loader.new<AbsInjected>()
        val result = injected["getProvide"]()["provide"]().obj
        Assertions.assertEquals("AbsProvides", result)
    }

    @Component
    class TestBasicTypeTarget {
        @Provides
        val provider: JInt = 1.boxed()
        private val needed: Int by di

        @Provides
        val providerL: JLong = 2L.boxed()
        private val neededL: Long by di

        @Provides
        val providerF: Float = 3f
        private val neededF: Float by di
        private val neededFJ: JFloat by di

        @Provides
        val providerD: Double = 4.0
        private val neededD: Double by di

        fun asserts() {
            assert(needed == 1)
            assert(neededL == 2L)
            assert(neededF.equals(3f))
            assert(neededFJ.equals(3f))
            assert(neededD.equals(4.0))
        }
    }

    @Test
    fun `test basic type`() {
        val containers = readContainers(TestBasicTypeTarget::class)
        val loader = containers.toContext().toClassLoader()
        val injectedTarget = loader.new<TestBasicTypeTarget>()
        injectedTarget["asserts"]().obj
    }

    @Component
    class TestBasicWithRequirement {
        class Source(val needed: Int)

        @Provides
        data class GlobalSource(
            val needed: Int, val nF: Float,
            val nD: Double, val nL: Long,
        )

        @Provides
        fun provideSource(needed: Int): Source = Source(needed)

        @Provides
        val providesNeeded = 114514

        @Provides
        val providesFloat = 2f

        @Provides
        val providesDouble = 3.0

        @Provides
        val providesLong = 4L

        val injected: Source by di
        val globalInjected: GlobalSource by di

        fun assertions() {
            Assertions.assertEquals(114514, injected.needed)
            Assertions.assertEquals(
                GlobalSource(114514, 2f, 3.0, 4L),
                globalInjected,
            )
        }
    }

    @Test
    fun `test basic type with requirements`() {
        val containers = readContainers4<
            TestBasicWithRequirement, TestBasicWithRequirement.Source,
            TestBasicWithRequirement.GlobalSource, GlobalProvides,
            >()
        val loader = containers.toContext().toClassLoader()
        val target = loader.new<TestBasicWithRequirement>()
        target["assertions"]().obj
    }

    @Test
    fun `test access private component prop`() {
        val containers = readContainers(
            AccessComponentFromDeps::class, DepsComponent::class,
        )
        val newLoader = containers.toContext().toClassLoader()
        val depsComponent = newLoader.new<DepsComponent>().obj
        val instance = newLoader.new<AccessComponentFromDeps>(depsComponent)
        val sb = instance["getSb"]().obj.toString()
        Assertions.assertEquals("Hello World!", sb)
    }

    @Test
    fun `test attach generic to requirements`() {
        val containers = readContainer<AttachGenericContainer<String>>()
        val loader = containers.toContext().toClassLoader()
        val component = loader.new<AttachGenericContainer<String>>()
        val charSet = component["getCharSet"]().obj
        val oneThingList = component["getOneThingList"]().obj
        Assertions.assertEquals(emptySet<CharSequence>(), charSet)
        Assertions.assertEquals(listOf("Hello"), oneThingList)
    }

    @Test
    fun `write double null check for synchronized`() {
        val containers = readContainers(SimpleContainer::class)
        val loader = containers.toContext().toClassLoader()
        loader.new<SimpleContainer>()
    }
}
