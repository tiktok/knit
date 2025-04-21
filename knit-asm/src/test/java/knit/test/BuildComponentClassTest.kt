package knit.test

import knit.Component
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.asKnitType
import knit.test.base.assertContentMatches
import knit.test.base.knitTypeOf
import knit.test.base.providesFunFromName
import knit.test.base.readComponents
import knit.test.base.readContainers3
import knit.test.base.readMetadataFrom
import knit.test.base.rootCause
import knit.test.base.toContext
import knit.test.sample.TestComponent
import knit.test.sample.TestCompositeComponent
import knit.test.sample.TestSingletonComponent
import knit.test.sample.providesGenericSingleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.KnitSimpleError
import tiktok.knit.plugin.descName
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.InjectedGetter
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.internalName
import tiktok.knit.plugin.toFieldAccess
import kotlin.reflect.jvm.javaMethod

/**
 * Created by yuejunyu on 2023/6/9
 * @author yuejunyu.0
 */
@TestTargetClass(ComponentClass::class)
class BuildComponentClassTest : KnitTestCase {
    @Test
    fun `read component class basically`() {
        val metadataContainer = readMetadataFrom<TestComponent<*>>()
        val testComponentName = TestComponent::class.internalName
        val componentClass = ComponentClass.from(metadataContainer)
        val listDesc = List::class.descName
        val typeParamA = KnitType.from(
            classifier = KnitClassifier(id = 0),
        ).toGeneric(
            bounds = listOf(CharSequence::class.asKnitType()),
        )
        assertEquals(listOf(typeParamA), componentClass.typeParams)
        val injectedGetters = listOf(
            InjectedGetter.from(
                component = componentClass.internalName,
                name = "getNeeded",
                type = knitTypeOf<List<String>>(),
            ),
            InjectedGetter.from(
                component = componentClass.internalName,
                name = "getNeededAndProvides",
                type = knitTypeOf<List<String>>(),
            ),
        )
        assertEquals(injectedGetters, componentClass.injectedGetters)

        val neededAndProvidesField = ProvidesMethod.from(
            containerClass = testComponentName,
            actualType = knitTypeOf<List<String>>(),
            desc = "()$listDesc",
            functionName = "getNeededAndProvides",
        )
        assertEquals(
            neededAndProvidesField,
            componentClass.providesFunFromName("getNeededAndProvides"),
        )

        val simpleProvidesField = ProvidesMethod.from(
            containerClass = testComponentName,
            actualType = List::class.asKnitType(
                typeParams = listOf(typeParamA),
            ),
            desc = "()$listDesc",
            functionName = "getSimpleProvides",
        )
        assertEquals(
            simpleProvidesField,
            componentClass.providesFunFromName("getSimpleProvides"),
        )

        val sbDesc = StringBuilder::class.descName
        val providesSbFun = ProvidesMethod.from(
            containerClass = testComponentName,
            actualType = StringBuilder::class.asKnitType(),
            desc = "()$sbDesc",
            functionName = "providesSb",
        )
        assertEquals(
            providesSbFun,
            componentClass.providesFunFromName("providesSb"),
        )

        // B
        val typeParamB = KnitType.from(
            classifier = KnitClassifier(id = 1),
        ).toGeneric(
            bounds = listOf(
                KnitType.from(
                    classifier = KnitClassifier(Exception::class.descName),
                ),
            ),
        )

        // List<A>
        val listAParam = KnitType.from(
            classifier = KnitClassifier(listDesc),
            typeParams = listOf(typeParamA),
        ).toGeneric()
        val providesGenericFun = ProvidesMethod.from(
            containerClass = testComponentName,
            // Map<List<A>, B>
            actualType = Map::class.asKnitType(
                typeParams = listOf(listAParam, typeParamB),
            ),
            desc = "()${Map::class.descName}",
            functionName = "providesGeneric",
            typeParams = listOf(typeParamB),
        )
        assertEquals(
            providesGenericFun,
            componentClass.providesFunFromName("providesGeneric"),
        )
        assertEquals(4, componentClass.provides.size)
    }

    @Test
    fun `test composite component`() {
        val metadataContainer = readMetadataFrom<TestCompositeComponent>()
        val componentClass = ComponentClass.from(metadataContainer)
        val compositeComponents = hashMapOf(
            "getAComponent" to CompositeComponent(KnitType.from("knit/test/sample/AComp")),
            "bComponent".toFieldAccess() to CompositeComponent(
                KnitType.from("knit/test/sample/BComp"), false,
            ),
        )
        assertEquals(compositeComponents, componentClass.compositeComponents)
    }

    @Test
    fun `test singleton`() {
        val metadataContainer = readMetadataFrom<TestSingletonComponent<*>>()
        val componentClass = ComponentClass.from(metadataContainer)
        val typeParams = listOf(
            KnitType.from(
                KnitClassifier.from(0),
            ).toGeneric(),
        )
        val internalName = TestSingletonComponent::class.internalName

        val listAType = List::class.asKnitType(
            typeParams = listOf(typeParams[0]),
        )
        val provides = listOf(
            ProvidesMethod.from(
                containerClass = internalName,
                desc = "()${List::class.descName}",
                functionName = "getProvidesSingletonField",
                actualType = listAType,
            ),
        )
        assertContentMatches(provides, componentClass.provides)
        assert(componentClass.singletons.isEmpty())
    }

    @Test
    fun `test static singleton`() {
        val method = ::providesGenericSingleton.javaMethod
        val clazz = method?.declaringClass?.kotlin
        requireNotNull(clazz)
        val (component) = readComponents(clazz)
        val singleton = KnitSingleton.from(
            global = true,
            getterFuncName = method.name,
            desc = "()${List::class.descName}",
            type = knitTypeOf<List<String>>(),
            threadSafe = true,
        )
        assertEquals(singleton, component.singletons.single())
    }

    @Component
    class A {
        @Component
        val b: B = B()
    }

    @Component
    class B {
        @Component
        val c: C = C()
    }

    class C {
        @Component
        val a: A = A()
    }

    @Test
    fun `test component loop`() {
        val containers = readContainers3<A, B, C>()
        val e = assertThrows<KnitSimpleError> {
            containers.toContext()
        }
        assertEquals(
            "Detect a loop when construct component: " +
                "${A::class.internalName} -> ${B::class.internalName} -> ${C::class.internalName} -> ${A::class.internalName}",
            e.rootCause().message,
        )
    }
}