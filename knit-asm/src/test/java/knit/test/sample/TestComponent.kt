package knit.test.sample

import knit.Component
import knit.Provides
import knit.Singleton
import knit.di

/**
 * Created by yuejunyu on 2023/6/9
 * @author yuejunyu.0
 */
@Component
class TestComponent<A : CharSequence> {
    val needed: List<String> by di

    @Provides
    val neededAndProvides: List<String> by di

    @Provides
    val simpleProvides: List<A> = emptyList()

    @Provides
    fun providesSb(): StringBuilder = StringBuilder()

    @Provides
    fun <B : Exception> providesGeneric(): Map<List<A>, B> = mapOf()
}

@Component
class TestSingletonComponent<A> {
    @Provides
    val providesSingletonField: List<A> = listOf()
}

@Provides
@Singleton
fun providesGenericSingleton(): List<String> = listOf()

@Component
class AComp

@Component
class BComp

class TestCompositeComponent(
    @Component val aComponent: AComp,
    @Component private val bComponent: BComp,
)
