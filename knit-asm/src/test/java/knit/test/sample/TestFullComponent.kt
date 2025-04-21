package knit.test.sample

import knit.Component
import knit.Factory
import knit.Loadable
import knit.Provides
import knit.Singleton
import knit.di

/**
 * Created by yuejunyu on 2023/6/16
 * @author yuejunyu.0
 */
@Component
class TestFullComponent(
    @Component val depsComponent: DepsComponent,
    @Provides val str: String,
) {
    val sb: StringBuilder by di
    val cs: CharSequence by di
    val singletonSet: Set<String> by di
}

@Component
class DepsComponent {
    @Component
    val deepDepsComponent: DeepDepsComponent = DeepDepsComponent()

    @Provides
    val sb: StringBuilder = StringBuilder("Hello World!")

    @Singleton
    @Provides
    fun provideSet(str: String): Set<String> = setOf("foo$str")
}

@Component
class AccessComponentFromDeps(
    @Component private val depsComponent: DepsComponent,
) {
    val sb: StringBuilder by di
}

@Component
class DeepDepsComponent {
    @Provides(CharSequence::class)
    val sb: StringBuilder = StringBuilder("Hello World! CharSequence~")
}

interface Obj {
    val test: String
}

@Provides
class GlobalTestObj : Obj {
    override val test: String = "Hello!"
}

@Provides
class FactoryInjectionObject {
    fun hello() = "Hello Factory!"
}

@Component
class FactoryInjectionTarget {
    private val factory: Factory<FactoryInjectionObject> by di

    private val loadable: Loadable<FactoryInjectionObject> by di
    fun getObj(): FactoryInjectionObject {
        return factory()
    }

    fun load(): String {
        var now = loadable.get()
        assert(now == null)
        loadable.load()
        now = loadable.get()
        assert(now is FactoryInjectionObject)
        loadable.unload()
        now = loadable.get()
        assert(now == null)
        now = loadable.load()
        return now.hello()
    }
}

interface StringProvider {
    @Provides
    fun provide(): String
}

@Provides(StringProvider::class)
class StringProviderImpl : StringProvider {
    override fun provide(): String {
        return "Interface Provides"
    }
}

@Component
class InterfaceProviderTarget {
    @Component
    val strProvider: StringProvider by di
    val str: String by di
}
