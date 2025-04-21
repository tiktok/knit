package knit.test

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.BuiltinInheritJudgement
import knit.test.base.KnitMock
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.asKnitArrayType
import knit.test.base.asTestBound
import knit.test.base.assertContentMatches
import knit.test.base.dynamicInjection
import knit.test.base.knitTypeOf
import knit.test.base.new
import knit.test.base.readComponents
import knit.test.base.readContainer
import knit.test.base.readContainers2
import knit.test.base.readContainers3
import knit.test.base.readContainers4
import knit.test.base.toComponent
import knit.test.base.toContext
import knit.test.sample.BinderSampleComp1
import knit.test.sample.BinderSampleComp2
import knit.test.sample.Comp3
import knit.test.sample.Comp4
import knit.test.sample.ListHolder
import knit.test.sample.SucceedInjectedComponent
import knit.test.sample.TestCannotMatchRequirements
import knit.test.sample.TestCannotMatchRequirementsTarget
import knit.test.sample.TestCannotProvidesParent
import knit.test.sample.TestGenericComponentCannotInject
import knit.test.sample.TestGenericComponentCannotInject2
import knit.test.sample.TestGenericComponentInject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.ComponentRecord
import tiktok.knit.plugin.NoProvidesFoundException
import tiktok.knit.plugin.TypeConflictException
import tiktok.knit.plugin.TypeConflictInCompositeException
import tiktok.knit.plugin.TypeConflictWBR
import tiktok.knit.plugin.descName
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.injection.InjectionBinder
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/6/7
 * @author yuejunyu.0
 */
@TestTargetClass(InjectionBinder::class)
class InjectionBinderTest : KnitTestCase {
    @Test
    fun `test buildInjectionFrom`() {
        val (_, comp3, thisComponent) = readComponents(
            BinderSampleComp1::class, Comp3::class, SucceedInjectedComponent::class,
        ).asTestBound()
        val injections = InjectionBinder.buildInjectionsForComponent(
            thisComponent, GlobalInjectionContainer(),
        )
        val providesCharFunc = comp3.provides[0]
        val providesArray = thisComponent.provides[0]
        val charsType = knitTypeOf<CharSequence>()
        val stringArrayType = String::class.asKnitArrayType()
        val expected = hashMapOf(
            "getChars" to Injection(
                charsType, providesCharFunc, Injection.From.COMPOSITE,
            ).dynamicInjection(),
            "getArray" to Injection(
                stringArrayType, providesArray, Injection.From.SELF,
            ).dynamicInjection(),
        )
        Assertions.assertEquals(expected, injections)
    }

    @Test
    fun `test build injection for generic component`() {
        val (listHolderComponent, comp4, targetComp) =
            readComponents(ListHolder::class, Comp4::class, TestGenericComponentInject::class)
                .asTestBound()
        val injections = InjectionBinder.buildInjectionsForComponent(
            targetComp,
            GlobalInjectionContainer(
                listOf(listHolderComponent),
            ),
            BuiltinInheritJudgement,
        )
        val providesFunction = comp4.provides[0]
        val listHolderType = knitTypeOf<ListHolder>()

        val sbProvidesFunction = providesFunction.copy(
            providesTypes = listOf(knitTypeOf<List<StringBuilder>>()),
        )
        val sbInjection = Injection(
            knitTypeOf<List<StringBuilder>>(), sbProvidesFunction, Injection.From.COMPOSITE,
        ).dynamicInjection()
        val listHolderProvides = listHolderComponent.provides[0]
        val recursiveInjection = Injection(
            listHolderType, listHolderProvides,
            listOf(
                Injection(
                    listHolderProvides.requirements[0], sbProvidesFunction, Injection.From.COMPOSITE,
                ).dynamicInjection(),
            ),
            Injection.From.GLOBAL,
        )
        assertContentMatches(
            listOf(sbInjection, recursiveInjection), injections.values,
        )
    }

    @Test
    fun `test fail build injection for generic component`() {
        val (_, targetComp) = readComponents(
            Comp4::class, TestGenericComponentCannotInject::class,
        ).asTestBound()
        val e = assertThrows<NoProvidesFoundException> {
            InjectionBinder.buildInjectionsForComponent(
                targetComp, GlobalInjectionContainer(), BuiltinInheritJudgement,
            )
        }
        Assertions.assertEquals(
            NoProvidesFoundException(
                targetComp.internalName,
                targetComp.injectedGetters[0].type,
                targetComp.compositeComponents.values.first().component.provides,
            ).message,
            e.message,
        )
    }

    @Test
    fun `test fail build injection for generic component but applicable type`() {
        val (_, targetComp) = readComponents(
            Comp4::class, TestGenericComponentCannotInject2::class,
        ).asTestBound()
        val e = assertThrows<NoProvidesFoundException> {
            InjectionBinder.buildInjectionsForComponent(
                targetComp, GlobalInjectionContainer(), BuiltinInheritJudgement,
            )
        }
        Assertions.assertEquals(
            NoProvidesFoundException(
                targetComp.internalName,
                targetComp.injectedGetters[0].type,
                targetComp.compositeComponents.values.first().component.provides,
            ).message,
            e.message,
        )
    }

    @Test
    fun `test fails when cannot provides its parent`() {
        val e = assertThrows<IllegalArgumentException> {
            readContainer<TestCannotProvidesParent>().toContext()
        }
        Assertions.assertEquals(
            """
            As a @Provides parent, ${TestCannotProvidesParent::class.descName} must extends from ${String::class.descName}
        """.trimIndent(),
            e.message,
        )
    }

    @Component
    class TestDupComponent(
        @Component val binderSampleComp1: BinderSampleComp1,
        @Component val binderSampleComp2: BinderSampleComp2,
    ) {
        val injectTarget: String by di
    }

    @Test
    fun `test fails when it has conflicts composite provides`() {
        val containers = readContainers3<BinderSampleComp1, BinderSampleComp2, TestDupComponent>()
        val e = assertThrows<TypeConflictInCompositeException> {
            containers.toContext().toClassLoader()
        }
        val testDupContainer = containers[2].toComponent()

        val component0 = containers[0].toComponent()
        val component1 = containers[1].toComponent()
        val typeConflictException = TypeConflictException(
            testDupContainer.internalName,
            knitTypeOf<String>(),
            listOf(
                component0.provides.first { it.functionName == "providesString" },
                component1.provides.first { it.functionName == "providesString" },
            ),
        )
        val typeConflictInCompositeException = TypeConflictInCompositeException(
            typeConflictException,
            ComponentRecord.from(
                listOf(
                    ComponentRecord(component0.internalName, listOf("getBinderSampleComp1")),
                    ComponentRecord(component1.internalName, listOf("getBinderSampleComp2")),
                ),
            ),
        )
        Assertions.assertEquals(
            typeConflictInCompositeException.message,
            e.message,
        )
    }

    @Component
    class TestNormalDupComponent {
        @Provides
        val s1: String = "s1"

        @Provides
        val s2: String = "s2"
        val injectTarget: String by di
    }

    @Test
    fun `test fails when it has conflicts provides`() {
        val containers = readContainer<TestNormalDupComponent>()
        val e = assertThrows<TypeConflictException> {
            containers.toContext().toClassLoader()
        }
        val component0 = containers[0].toComponent()
        val typeConflictException = TypeConflictException(
            component0.internalName,
            knitTypeOf<String>(),
            listOf(
                component0.provides.first { it.functionName == "getS1" },
                component0.provides.first { it.functionName == "getS2" },
            ),
        )
        Assertions.assertEquals(
            typeConflictException.message,
            e.message,
        )
    }

    @Provides
    class TestDupStringHolder(str: String)

    @Component
    class TestDupComponentInRequirement(
        @Component val binderSampleComp1: BinderSampleComp1,
        @Component val binderSampleComp2: BinderSampleComp2,
    ) {
        val injected: TestDupStringHolder by di
    }

    @Test
    fun `test fails when it has conflicts provides in requirements`() {
        val containers = readContainers4<
            BinderSampleComp1, BinderSampleComp2,
            TestDupComponentInRequirement, TestDupStringHolder,
            >()
        val (comp1, comp2, dupContainer, strHolder) = containers
        val e = assertThrows<TypeConflictWBR> {
            containers.toContext().toClassLoader()
        }
        val strHolderProvides = strHolder.toComponent().provides.first()
        val testDupContainer = dupContainer.toComponent()
        val typeConflictException = TypeConflictWBR(
            strHolderProvides,
            testDupContainer.internalName,
            knitTypeOf<String>(),
            listOf(
                comp1.toComponent().provides.first { it.functionName == "providesString" },
                comp2.toComponent().provides.first { it.functionName == "providesString" },
            ),
        )
        Assertions.assertEquals(
            typeConflictException.message,
            e.message,
        )
    }

    @Test
    fun `test fails when cannot match provides requirements`() {
        val containers =
            readContainers2<TestCannotMatchRequirements, TestCannotMatchRequirementsTarget>()
        val e = assertThrows<NoProvidesFoundException> {
            containers.toContext().toClassLoader()
        }
        val noProvidesFoundException = NoProvidesFoundException(
            TestCannotMatchRequirementsTarget::class.internalName,
            knitTypeOf<String>(), emptyList(),
        )
        Assertions.assertEquals(
            noProvidesFoundException.message,
            e.message,
        )
    }

    @Component
    class ParentComposite {
        @Provides
        val str: String = "foo"
    }

    @Component
    class ChildComposite(@Component private val parent: ParentComposite) {
        val strCouldInject: String by di
    }

    @Component
    class ChildChildComposite(@Component private val parent: ChildComposite) {
        val str: String by di
    }

    @Test
    fun `test fails when want to access private composite component`() {
        val containers = readContainers3<ParentComposite, ChildComposite, ChildChildComposite>()
        val e = assertThrows<NoProvidesFoundException> {
            containers.toContext().toClassLoader()
        }
        val noProvidesFoundException = NoProvidesFoundException(
            ChildChildComposite::class.internalName, knitTypeOf<String>(), emptyList(),
        )
        Assertions.assertEquals(noProvidesFoundException, e)
    }

    @Component
    class PrivateProvides {
        @Provides
        private fun providesString(): String = "foo"
    }

    class PrivateProvidesTarget(
        @Component val parent: PrivateProvides
    ) {
        val injected: String by di
    }

    @Test
    fun `test fails when private provides`() {
        val containers = readContainers2<PrivateProvides, PrivateProvidesTarget>()
        val e = assertThrows<NoProvidesFoundException> {
            containers.toContext().toClassLoader()
        }
        val expected = NoProvidesFoundException(
            PrivateProvidesTarget::class.internalName, knitTypeOf<String>(), emptyList(),
        )
        Assertions.assertEquals(expected, e)
    }

    @Component
    class PrivateSelfProvides {
        @Provides
        private fun providesString(): String = "foo"
        val injected: String by di
    }

    @Test
    fun `test success when provides provides in self component`() {
        val containers = readContainer<PrivateSelfProvides>()
        val loader = containers.toContext().toClassLoader()
        val selfProvides = loader.new<PrivateSelfProvides>()
        val injected = selfProvides["getInjected"]().obj
        Assertions.assertEquals("foo", injected)
    }

    @KnitMock
    open class OverrideParentTestParent {
        @Provides
        val s: String = "p"
    }

    @KnitMock
    class OverrideParentTestTargetNot {
        @Component
        val parent: OverrideParentTestParent = OverrideParentTestParent()

        val s: String by di
    }

    @Test
    fun `test not override composite injection`() {
        Assertions.assertEquals("p", OverrideParentTestTargetNot().s)
    }

    @KnitMock
    class OverrideParentTestTargetYes {
        @Component
        val parent: OverrideParentTestParent = OverrideParentTestParent()

        @Provides
        val overrideS: String = "o"

        val s: String by di
    }

    @Test
    fun `test override composite injection`() {
        Assertions.assertEquals("o", OverrideParentTestTargetYes().s)
    }

    @KnitMock
    class OverrideParentTestTargetWithExtend : OverrideParentTestParent() {
        @Provides
        val c: String = "c"

        val childS: String by di
    }

    @Test
    fun `test override parent provides`() {
        Assertions.assertEquals("c", OverrideParentTestTargetWithExtend().childS)
    }
}
