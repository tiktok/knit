package knit.test.sample

import knit.Component
import knit.Provides
import knit.di

/**
 * Created by yuejunyu on 2023/6/12
 * @author yuejunyu.0
 */

@Component
class BinderSampleComp1 {
    @Provides
    fun providesString(): String = ""

    @Provides
    fun providesSb() = StringBuilder()
}

@Component
class BinderSampleComp2 {
    @Provides
    fun providesString(): String = ""

    @Provides
    fun providesCharSeq(): CharSequence = StringBuilder()
}

@Component
class Comp3 {
    @Provides
    fun providesCharSeq(): CharSequence = StringBuilder()
}

@Component
class SucceedInjectedComponent(
    @Component val binderSampleComp1: BinderSampleComp1,
    @Component val comp3: Comp3,
) {
    @Provides
    val selfProvides: Array<String> = emptyArray()

    val chars: CharSequence by di
    val array: Array<String> by di
}

@Component
class Comp4<T : CharSequence> {
    @Provides
    val list: List<T> = listOf()
}

@Provides
class ListHolder(list: List<StringBuilder>)

@Component
class TestGenericComponentInject(
    @Component val comp4: Comp4<StringBuilder>,
) {
    val sbList: List<StringBuilder> by di
    val recursiveInject: ListHolder by di
}

@Component
class TestGenericComponentCannotInject(
    @Component val comp4: Comp4<StringBuilder>,
) {
    val intList: List<Int> by di
}

@Component
class TestGenericComponentCannotInject2(
    @Component val comp4: Comp4<StringBuilder>,
) {
    val intList: List<CharSequence> by di
}

@Provides(String::class)
class TestCannotProvidesParent : CharSequence by "foo"

@Provides
class TestCannotMatchRequirements(r: String)

@Component
class TestCannotMatchRequirementsTarget {
    val test: TestCannotMatchRequirements by di
}
