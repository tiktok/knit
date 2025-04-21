package knit.test.base

import com.google.common.collect.HashMultiset
import knit.Named
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.KnitInternalError
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.asMetadataContainer
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.BoundComponentMapping
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ComponentMapping
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitGenericType
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.element.TypeParamIdMapper
import tiktok.knit.plugin.element.attach2BoundMapping
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.internalName
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.metadata.KmTypeParameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVariance
import kotlin.reflect.full.companionObject
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


/**
 * Created by yuejunyu on 2023/6/7
 * @author yuejunyu.0
 */
inline fun <reified T> readMetadataFrom(companion: Boolean = false): MetadataContainer {
    return readMetadataFrom(T::class, companion)
}

fun readAsNode(clazz: Class<*>): ClassNode {
    return readAsNode(clazz.name)
}

fun readAsNode(vararg klazz: KClass<*>): List<ClassNode> {
    return klazz.map { readAsNode(it.java.name) }
}

fun readFunctionContainingNode(kFunction: KFunction<*>): ClassNode {
    val clazz = kFunction.javaMethod!!.declaringClass
    return readAsNode(clazz)
}

fun readAsNode(className: String): ClassNode {
    val node = ClassNode()
    ClassReader(className).accept(
        node, ClassReader.EXPAND_FRAMES,
    )
    return node
}

fun readMetadataFrom(klass: KClass<*>, companion: Boolean): MetadataContainer {
    val clazz = if (companion) klass.companionObject?.java else klass.java
    requireNotNull(clazz)
    val metadata = readAsNode(clazz).asMetadataContainer()
    return requireNotNull(metadata)
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TestTargetClass(vararg val clazz: KClass<*>)

fun ComponentClass.providesFunFromName(name: FuncName): ProvidesMethod {
    return provides.first { it.functionName == name }
}

fun MetadataContainer.toComponent(): ComponentClass {
    return ComponentClass.from(this)
}

fun readComponents(vararg classes: KClass<*>): List<ComponentClass> {
    return classes.map { readMetadataFrom(it, false).toComponent() }
}

fun readContainers(vararg klass: KClass<*>): List<MetadataContainer> {
    return klass.map { readMetadataFrom(it, false) }
}

inline fun <reified C1> readContainer(): List<MetadataContainer> {
    return readContainers(C1::class)
}

inline fun <reified C1, reified C2> readContainers2(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class)
}

inline fun <reified C1, reified C2, reified C3> readContainers3(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class, C3::class)
}

inline fun <reified C1, reified C2, reified C3, reified C4> readContainers4(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class, C3::class, C4::class)
}

inline fun <reified C1, reified C2, reified C3, reified C4, reified C5> readContainers5(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class, C3::class, C4::class, C5::class)
}

inline fun <reified C1, reified C2, reified C3, reified C4, reified C5, reified C6> readContainers6(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class, C3::class, C4::class, C5::class, C6::class)
}

inline fun <reified C1, reified C2, reified C3, reified C4, reified C5, reified C6, reified C7> readContainers7(): List<MetadataContainer> {
    return readContainers(C1::class, C2::class, C3::class, C4::class, C5::class, C6::class, C7::class)
}

fun List<ComponentClass>.asTestBound(map: BoundComponentMapping = hashMapOf()): List<BoundComponentClass> {
    val componentMapping = asComponentMapping()
    return map {
        it.attach2BoundMapping(componentMapping, map)
    }
}

fun List<ComponentClass>.asComponentMapping(): ComponentMapping {
    return TestContext.BuiltinComponentMapping(associateBy { it.internalName })
}

fun KClass<*>.asKnitType(
    nullable: Boolean = false, named: String = "",
    typeParams: List<KnitGenericType> = listOf()
): KnitType {
    return KnitType.from(KnitClassifier.from(this), nullable, named, typeParams)
}

inline fun <reified T> knitTypeOf(named: String = ""): KnitType {
    val type = typeOf<T>()
    return knitTypeOf(type, named)
}

fun knitTypeOf(type: KType, named: String = ""): KnitType {
    val clazz = type.classifier as KClass<*>
    val name = named.ifEmpty { type.getNamed() }
    val genericTypes = type.arguments.map { kTypeProjection ->
        val variance = when (kTypeProjection.variance) {
            KVariance.INVARIANT -> KnitGenericType.NO_VARIANCE
            KVariance.IN -> KnitGenericType.IN
            KVariance.OUT -> KnitGenericType.OUT
            null -> null
        }
        val argName = type.getNamed()
        KnitGenericType(
            variance ?: KnitGenericType.NO_VARIANCE,
            kTypeProjection.type?.let { knitTypeOf(it, argName) },
        )
    }
    return clazz.asKnitType(type.isMarkedNullable, name, genericTypes)
}

private fun KType.getNamed(): String {
    val argNamed = annotations.filterIsInstance<Named>().firstOrNull()
    if (argNamed != null) {
        return argNamed.value.ifEmpty {
            argNamed.qualifier.internalName
        }
    }
    return ""
}

fun KClass<*>.asKnitArrayType(
    nullable: Boolean = false, named: String = "",
    typeParams: List<KnitGenericType> = listOf()
): KnitType {
    val origin = KnitClassifier.from(this)
    return KnitType.from(KnitClassifier.fromArray(origin), nullable, named, typeParams)
}

/** this method will compare it without order */
fun <T> assertContentMatches(
    expected: Collection<T>, actual: Collection<T>,
) {
    val expectedSet = HashMultiset.create(expected)
    val actualSet = HashMultiset.create(actual)
    Assertions.assertEquals(expectedSet, actualSet)
}

object BuiltinInheritJudgement : InheritJudgement {
    override fun inherit(thisName: InternalName, parentName: InternalName): Boolean {
        return try {
            val thisClass = Class.forName(Type.getType("L$thisName;").className)
            val parentClass = Class.forName(Type.getType("L$parentName;").className)
            parentClass.isAssignableFrom(thisClass)
        } catch (e: ClassNotFoundException) {
            // ignore result
            false
        }
    }
}

/** auto attach `this` component */
fun Injection.dynamicInjection(): Injection {
    val type = providesMethod.providesTypes.first()
    val selfInjection = Injection(type, providesMethod, emptyList(), from)
    return copy(
        requirementInjections = listOf(selfInjection) + requirementInjections,
    )
}

class DelegateClassLoader(
    val context: KnitContext, parent: ClassLoader, classNodes: List<ClassNode>
) : ClassLoader(parent) {
    private val nodeNames = classNodes.associateBy { Type.getObjectType(it.name).className }
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(this) {
            if (name !in nodeNames) {
                if (!name.startsWith("knit")) return parent.loadClass(name)
                val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
                ClassReader(name).accept(classWriter, ClassReader.EXPAND_FRAMES)
                val byteArray = classWriter.toByteArray()
                return defineClass(name, byteArray, 0, byteArray.size)
            }
            val c = findLoadedClass(name)
            return c ?: transformedClass(name)
        }
    }

    private fun transformedClass(name: String): Class<*> {
        val arr = getClassContent(name)
        return defineClass(name, arr, 0, arr.size)
    }

    fun getClassContent(name: String): ByteArray {
        val visitor = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        val node = requireNotNull(nodeNames[name])
        node.accept(visitor)
        return visitor.toByteArray()
    }

    fun dump(dir: File = File("").absoluteFile) {
        for ((_, node) in nodeNames) {
            val visitor = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            val file = File(dir, "${node.name}.class")
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (file.exists()) file.delete()
            file.createNewFile()
            node.accept(visitor)
            file.writeBytes(visitor.toByteArray())
        }
    }
}

fun KnitContext.childLoader(classNodes: List<ClassNode>): DelegateClassLoader {
    return DelegateClassLoader(this, this::class.java.classLoader, classNodes)
}

fun String.validJavaIdentifier(): String {
    return replace('/', '_').filter { it.isJavaIdentifierPart() }
}

object AlwaysFailedIdMapper : TypeParamIdMapper {
    override fun invoke(id: Int): KmTypeParameter {
        throw IllegalAccessException("Always failed when access this mapper")
    }
}

@OptIn(ExperimentalContracts::class)
inline fun assertKnitInternalError(message: String, action: () -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val e = assertThrows<KnitInternalError> {
        action()
    }
    Assertions.assertEquals(message, e.message)
}

fun Throwable.rootCause(): Throwable {
    var current: Throwable = this
    while (true) {
        current = current.cause ?: return current
    }
}
