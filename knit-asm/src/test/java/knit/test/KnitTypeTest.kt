package knit.test

import knit.Component
import knit.IntoList
import knit.JInt
import knit.Named
import knit.Provides
import knit.di
import knit.test.base.BuiltinInheritJudgement
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.assertKnitInternalError
import knit.test.base.knitTypeOf
import knit.test.base.readContainer
import knit.test.base.readContainers2
import knit.test.base.toComponent
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.Type
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitGenericType
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/4
 * @author yuejunyu.0
 */
@TestTargetClass(KnitType::class, KnitGenericType::class)
class KnitTypeTest : KnitTestCase {
    class SampleProvides {
        @Provides
        fun providesTyped(r1: @Named("bar") String)
            : @Named(qualifier = KnitTypeTest::class) String = "foo"
    }

    @Named("foo")
    @Provides
    class MyTypeRawProvide

    @Named(qualifier = KnitTypeTest::class)
    @Provides
    class MyTypeClazzProvide

    @Component
    class TestNullable {
        @Provides
        fun providesNullable(): String? = null
    }

    class TPContainer<T : List<T>>(d: T) : List<T> by d

    @Provides
    class TPContainerFailed<T : List<T>>(d: T) : List<T> by d

    @Test
    fun `test named km type`() {
        val (sample) = readContainer<SampleProvides>()
        val provides = sample.toComponent().provides.first()
        val r1Type = provides.requirements[0]
        val returnType = provides.providesTypes.first()

        val assertR1Type = knitTypeOf<String>("bar")
        val assertReturnType = knitTypeOf<String>(KnitTypeTest::class.internalName)
        Assertions.assertEquals(assertReturnType, returnType)
        Assertions.assertEquals(assertReturnType.toString(), returnType.toString())
        Assertions.assertEquals(assertR1Type, r1Type)
    }

    @Test
    fun `test named node type`() {
        val (rawProvide, clazzProvide) = readContainers2<MyTypeRawProvide, MyTypeClazzProvide>().map {
            it.toComponent()
        }
        val rawType = rawProvide.provides[0].providesTypes[0]
        val clazzType = clazzProvide.provides[0].providesTypes[0]

        val assertRawType = knitTypeOf<MyTypeRawProvide>("foo")
        val assertClazzType = knitTypeOf<MyTypeClazzProvide>(KnitTypeTest::class.internalName)
        Assertions.assertEquals(assertRawType, rawType)
        Assertions.assertEquals(assertClazzType, clazzType)
    }

    @Test
    fun `print genericType`() {
        val type0 = KnitGenericType(
            KnitGenericType.NO_VARIANCE, knitTypeOf<KnitTypeTest>(),
            listOf(knitTypeOf<String>()),
        ).toString()
        val type1 = KnitGenericType(
            KnitGenericType.IN, knitTypeOf<KnitTypeTest>(),
            listOf(knitTypeOf<String>()),
        ).toString()
        val type2 = KnitGenericType(
            KnitGenericType.OUT, knitTypeOf<KnitTypeTest>(),
            listOf(knitTypeOf<String>()),
        ).toString()
        val type3 = KnitGenericType().toString()
        val testFqn = KnitTypeTest::class.fqn
        val stringFqn = String::class.fqn
        Assertions.assertEquals("$testFqn : $stringFqn", type0)
        Assertions.assertEquals("in $testFqn : $stringFqn", type1)
        Assertions.assertEquals("out $testFqn : $stringFqn", type2)
        Assertions.assertEquals("*", type3)
    }

    @Test
    fun `test failed for impossible genericType`() {
        val message = "Knit generic type must be one of those variance: in/out or no variance."
        assertKnitInternalError(message) {
            KnitGenericType(variance = 100, type = knitTypeOf<String>()).toString()
        }
    }

    @Test
    fun `test failed for nullable`() {
        val nullableComponent = readContainer<TestNullable>()[0].toComponent()
        val available = knitTypeOf<String>().availableFor(
            nullableComponent.provides[0].providesTypes[0],
            BuiltinInheritJudgement,
        )
        Assertions.assertFalse(available)
    }

    @Test
    fun `test inherit`() {
        val listType = knitTypeOf<List<*>>()
        val collectionType = knitTypeOf<Collection<*>>()
        val inherit = listType.inherit(collectionType, BuiltinInheritJudgement)
        Assertions.assertTrue(inherit)
    }

    @Test
    fun `test failed when type param`() {
        val listType = knitTypeOf<List<*>>()
        val classifierType = KnitType.from(KnitClassifier(id = 1))
        val inherit = listType.inherit(classifierType, BuiltinInheritJudgement)
        Assertions.assertFalse(inherit)
    }

    @Test
    fun `test availableFor`() {
        val charSequenceType = KnitGenericType(
            KnitGenericType.IN,
            KnitType.from(
                KnitClassifier(id = 0),
            ),
            bounds = listOf(knitTypeOf<CharSequence>()),
        )
        val stringType = knitTypeOf<String>().toGeneric(KnitGenericType.NO_VARIANCE)
        val charsAvailableForString = charSequenceType.availableFor(
            stringType, BuiltinInheritJudgement,
        )
        val stringAvailableForChars = stringType.availableFor(
            charSequenceType, BuiltinInheritJudgement,
        )
        Assertions.assertTrue(charsAvailableForString)
        Assertions.assertFalse(stringAvailableForChars)
    }

    @Test
    fun `test availableFor basic type conversions`() {
        val intType = knitTypeOf<Int>()
        val integerType = KnitType.from("java/lang/Integer")
        val boxResult = intType.availableFor(integerType, BuiltinInheritJudgement)
        val unboxResult = integerType.availableFor(intType, BuiltinInheritJudgement)
        Assertions.assertTrue(boxResult)
        Assertions.assertTrue(unboxResult)
    }

    @Test
    fun `test failed when type parameter contains recursive bounds`() {
        val (tpContainer, tpContainerFailed) = readContainers2<TPContainer<*>, TPContainerFailed<*>>()
        tpContainer.toComponent()
        val e = assertThrows<IllegalArgumentException> {
            tpContainerFailed.toComponent()
        }
        Assertions.assertEquals(
            "type parameter <T>(id:0) contains recursive bounds: (kotlin/collections/List<0>)",
            e.message,
        )
    }

    @Test
    fun `test adaptBasicType`() {
        val knitType = knitTypeOf<Int>()
        val integerJavaClass = JInt::class.java
        val adapted = knitType.adaptBasicType(Type.getType(integerJavaClass))
        val intAdapted = knitType.adaptBasicType(Type.INT_TYPE)
        Assertions.assertEquals(Type.getDescriptor(integerJavaClass), adapted.classifier.desc)
        Assertions.assertEquals(Type.INT_TYPE.descriptor, intAdapted.classifier.desc)
    }

    class TypeBoundsTest {
        @Provides
        @IntoList
        fun provider(): Class<String> = String::class.java
        val r: List<Class<out CharSequence>> by di
    }

    @Test
    fun `test type bounds for by di and providers`() {
        readContainer<TypeBoundsTest>().toContext()
    }
}