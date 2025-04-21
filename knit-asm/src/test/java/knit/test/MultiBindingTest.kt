package knit.test

import knit.Component
import knit.IntoList
import knit.IntoMap
import knit.IntoSet
import knit.Provides
import knit.di
import knit.internal.MultiBindingBuilder
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.assertContentMatches
import knit.test.base.new
import knit.test.base.readContainer
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.MultiBindingType
import tiktok.knit.plugin.injection.MultiBindingIF

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
@TestTargetClass(MultiBindingIF::class, MultiBindingBuilder::class, MultiBindingType::class)
class MultiBindingTest : KnitTestCase {
    @Component
    class StringComponent {
        @Provides
        @IntoList
        fun provideFoo(): String {
            return "foo"
        }

        @Provides
        @IntoList
        @IntoSet
        fun provideBar(): String {
            return "bar"
        }

        @Provides
        @IntoMap
        fun providesPair(): Pair<String, String> = "a" to "b"

        @Provides
        @IntoMap
        fun providesPairC(): Pair<String, String> = "c" to "e"

        val stringS: Set<String> by di
        val stringL: List<String> by di
        val stringM: Map<String, String> by di
    }

    @Test
    fun `inject multi strings`() {
        val containers = readContainer<StringComponent>()
        val classLoader = containers.toContext().toClassLoader()
        val stringComponent = classLoader.new<StringComponent>()
        val stringS = stringComponent["getStringS"]().obj
        assertEquals(setOf("bar"), stringS)
        val stringL = stringComponent["getStringL"]().obj<List<String>>()
        assertContentMatches(listOf("foo", "bar"), stringL)
        val stringM = stringComponent["getStringM"]().obj
        assertEquals(hashMapOf("a" to "b", "c" to "e"), stringM)
    }

    @Component
    class StringNormalComponent {
        @Provides
        @IntoList
        fun provideFoo(): String {
            return "foo"
        }

        @Provides
        @IntoList(true)
        fun provideBar(): String {
            return "bar"
        }

        val string: String by di
        val stringL: List<String> by di
    }

    @Test
    fun `inject normal object also in multibinding`() {
        val containers = readContainer<StringNormalComponent>()
        val classLoader = containers.toContext().toClassLoader()
        val stringComponent = classLoader.new<StringNormalComponent>()
        val string = stringComponent["getString"]().obj
        assertEquals("foo", string)
        val stringL = stringComponent["getStringL"]().obj<List<String>>()
        assertContentMatches(listOf("foo", "bar"), stringL)
    }

    // define interface in api module
    @KnitMock
    interface Api

    // impl1 for API
    @Provides(Api::class)
    @IntoList
    @KnitMock
    class Impl1 : Api

    // impl2 for API
    @KnitMock
    @IntoList
    class Impl2 @Provides(Api::class) constructor() : Api

    // inject api as list in Usage
    @Component
    @KnitMock
    object UsagePlace {
        val allApis: List<Api> by di
    }

    @Test
    fun `collect list through provides parent`() {
        assertEquals(2, UsagePlace.allApis.size)
        assert(UsagePlace.allApis.any { it is Impl1 })
        assert(UsagePlace.allApis.any { it is Impl2 })
    }
}