// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmConstructor
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeParameter
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.jvm.syntheticMethodForAnnotations
import kotlin.metadata.kind

/**
 * Created by yuejunyu on 2023/6/5
 *
 * A container that combines kotlin metadata and ASM ClassNode to provide more comprehensive metadata retrieval.
 * It also implements the regular [KmDeclarationContainer] through delegation of [containerDelegate].
 *
 * @property node The ASM [ClassNode] object associated with this container.
 * @property containerDelegate Usually [KmClass] or [KmPackage].
 * @author yuejunyu.0
 */
class MetadataContainer(
    val node: ClassNode,
    private val containerDelegate: KmDeclarationContainer,
) : KmDeclarationContainer by containerDelegate {
    val name: InternalName = node.name
    private val methodMap: Map<JvmMethodSignature, MethodNode> = node.methods.associateBy {
        JvmMethodSignature(it.name, it.desc)
    }

    fun kmClassOrNull(): KmClass? = containerDelegate as? KmClass

    val constructors: List<KmConstructor> = kmClassOrNull()?.constructors.orEmpty()
    val typeParameters: List<KmTypeParameter> = kmClassOrNull()?.typeParameters.orEmpty()
    val supertypes: List<KmType> = kmClassOrNull()?.supertypes.orEmpty()
    val isInterface: Boolean
        get() {
            val kmClass = kmClassOrNull() ?: return false
            val kind = kmClass.kind
            return kind == ClassKind.INTERFACE
        }

    private val fieldNodeMap = node.fields.associateBy { it.name }
    fun getFieldNodeOrNull(propertyName: String): FieldNode? {
        return fieldNodeMap[propertyName]
    }

    fun getPropertyAnnotations(property: KmProperty): List<AnnotationNode> {
        val signature = property.syntheticMethodForAnnotations
        // all annotations from annotation synthetic method
        val allAnnotations = arrayListOf<AnnotationNode>()
        if (signature != null) {
            allAnnotations += getMethod(signature).allAnnotations.toMutableList()
        }
        // all annotations from getter
        val getter = getPropGetterOrNull(property)
        if (getter != null) {
            allAnnotations += getter.allAnnotations
        }
        return allAnnotations
    }

    fun getPropGetter(kmProperty: KmProperty): MethodNode {
        return requireNotNull(getPropGetterOrNull(kmProperty)) {
            "property: [${kmProperty.name}] in $name " +
                "must have a getter function, please **not make** property is `private` " +
                "or not add `@JvmField` on it " +
                "or use method with @Provides instead of provide a property."
        }
    }

    fun getPropGetterOrNull(kmProperty: KmProperty): MethodNode? {
        return kmProperty.getterSignature?.let { getMethod(it) }
    }

    fun getFunctionAnnotations(kmFunction: KmFunction): List<AnnotationNode> {
        val signature = kmFunction.signature ?: return emptyList()
        return getMethod(signature).allAnnotations
    }

    fun getConstructorAnnotations(kmConstructor: KmConstructor):List<AnnotationNode>{
        val signature = kmConstructor.signature ?: return emptyList()
        return getMethod(signature).allAnnotations
    }

    fun getMethod(signature: JvmMethodSignature): MethodNode {
        return methodMap[signature] ?: knitInternalError(
            "knit get method [$signature] from ${node.name} failed! " +
                "In some cases, it is because of you are uses @Component at property in abstract class, " +
                "change to @get:Component to avoid this problem.",
        )
    }

    fun getMethodNode(kmFunction: KmFunction): MethodNode {
        val signature = kmFunction.signature ?: knitInternalError(
            "get method node for [${kmFunction.name}] in $name failed.",
        )
        return getMethod(signature)
    }

    fun getMethodNode(kmConstructor: KmConstructor): MethodNode {
        val signature = kmConstructor.signature ?: knitInternalError(
            "get method node for <init> in $name failed.",
        )
        return getMethod(signature)
    }
}

// return null if it isn't a kotlin source code or haven't Metadata annotation
// or multi file, or cannot recognize
fun ClassNode.asMetadataContainer(): MetadataContainer? {
    val allAnnotations = allAnnotations
    val metadataAnnotation =
        allAnnotations.firstOrNull { it.desc == metadataDescriptor } ?: return null

    var kind: Int? = null
    var metadataVersion: IntArray? = null

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    var bytecodeVersion: IntArray? = null // bytecode version will be removed later, but reserve currently
    var data1: Array<String>? = null
    var data2: Array<String>? = null
    var extraString: String? = null
    var packageName: String? = null
    var extraInt: Int? = null

    metadataAnnotation.onEach { attrName, value ->
        when (attrName) {
            Metadata::kind.callName -> kind = value.cast()
            Metadata::metadataVersion.callName -> metadataVersion = value.readIntArray()
            Metadata::bytecodeVersion.callName -> bytecodeVersion = value.readIntArray()
            Metadata::data1.callName -> data1 = value.readStringArray()
            Metadata::data2.callName -> data2 = value.readStringArray()
            Metadata::extraString.callName -> extraString = value.cast()
            Metadata::packageName.callName -> packageName = value.cast()
            Metadata::extraInt.callName -> extraInt = value.cast()
        }
    }

    val metadata = Metadata(
        kind, metadataVersion, data1, data2, extraString, packageName, extraInt,
    )
    val container = KotlinClassMetadata.readLenient(metadata).asContainer() ?: return null
    return MetadataContainer(this, container)
}

private fun KotlinClassMetadata.asContainer(): KmDeclarationContainer? {
    val container: KmDeclarationContainer? = when (this) {
        is KotlinClassMetadata.Class -> kmClass
        is KotlinClassMetadata.FileFacade -> kmPackage
        else -> null
    }
    return container
}

private inline fun <reified T> Any.cast(): T = this as T
private fun Any.readIntArray(): IntArray = cast<List<Int>>().toIntArray()
private fun Any.readStringArray(): Array<String> = cast<List<String>>().toTypedArray()

private val metadataDescriptor = Metadata::class.descName
