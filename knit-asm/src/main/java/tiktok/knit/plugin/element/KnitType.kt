// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import knit.Named
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.Printer
import tiktok.knit.plugin.allAnnotations
import tiktok.knit.plugin.basicTypeWrapperDesc
import tiktok.knit.plugin.basicTypeWrappers
import tiktok.knit.plugin.basicTypes
import tiktok.knit.plugin.callName
import tiktok.knit.plugin.element.KnitGenericType.Companion.NO_VARIANCE
import tiktok.knit.plugin.namedDesc
import tiktok.knit.plugin.namedInternalName
import tiktok.knit.plugin.onEach
import tiktok.knit.plugin.toInternalName
import kotlin.metadata.KmAnnotation
import kotlin.metadata.KmAnnotationArgument
import kotlin.metadata.KmType
import kotlin.metadata.isNullable
import kotlin.metadata.jvm.annotations
import kotlin.metadata.jvm.toJvmInternalName

/**
 * Used to describe a type. The required type may not only be a normal [KnitClassifier], but may also contain generic parameters.
 * @param typeParams such as: `List<out T>`, where out T is [KnitGenericType]
 */
data class KnitType(
    val classifier: KnitClassifier,
    val nullable: Boolean,
    val named: String,
    val typeParams: List<KnitGenericType>,
) {
    inline val descName: InternalName get() = classifier.desc
    inline val internalName: InternalName get() = descName.toInternalName()

    fun availableFor(neededType: KnitType, inheritJudgement: InheritJudgement): Boolean {
        // nullable / named
        if (nullable != neededType.nullable ||
            named != neededType.named // nullable we be treated as different type
        ) return false

        // type parameter
        if (!classifier.isTypeParameter()) {
            val thisDesc = classifier.desc
            val neededDesc = neededType.classifier.desc
            if (classifier.desc != neededType.classifier.desc) {
                // basic type judgement
                val basicTypeIndex = basicTypes.indexOf(neededDesc)
                if (basicTypeIndex != -1) {
                    val matchedWrapper = basicTypeWrapperDesc[basicTypeIndex]
                    return thisDesc == matchedWrapper
                }
                val wrappedTypeIndex = basicTypeWrapperDesc.indexOf(neededDesc)
                if (wrappedTypeIndex != -1) {
                    val matchedBasic = basicTypes[wrappedTypeIndex]
                    return thisDesc == matchedBasic
                }
                return false
            }
        }

        // then we will judge the type params
        val neededTypeParams = neededType.typeParams
        if (neededTypeParams.size != typeParams.size) return false
        for (paramIndex in typeParams.indices) {
            val originParam = typeParams[paramIndex]
            val requiredParam = neededTypeParams[paramIndex]
            if (!originParam.availableFor(requiredParam, inheritJudgement)) {
                return false
            }
        }
        return true
    }

    /** inherit just check their inherit, it will not check type parameters */
    fun inherit(parent: KnitType, inheritJudgement: InheritJudgement): Boolean {
        val parentClassifier = parent.classifier
        return if (classifier.isTypeParameter() || parentClassifier.isTypeParameter()) {
            // parameter inherit are not support yet
            false
        } else {
            val classifierDesc = classifier.desc
            val parentDesc = parentClassifier.desc
            return if (classifierDesc == parentDesc) true
            else inheritJudgement(classifier.desc, parentClassifier.desc)
        }
    }

    fun toGeneric(
        variance: Int = NO_VARIANCE, bounds: List<KnitType> = emptyList()
    ): KnitGenericType {
        return KnitGenericType(variance, this, bounds)
    }

    /** redirect basic type to wrapper type if needed (it uses method node type actually) */
    fun adaptBasicType(methodNode: MethodNode): KnitType {
        return adaptBasicType(Type.getMethodType(methodNode.desc).returnType)
    }

    /** @see [adaptBasicType] */
    fun adaptBasicType(type: Type): KnitType {
        if (classifier.desc !in basicTypes) return this
        val returnTypeName: InternalName = type.internalName
        val classifier = if (returnTypeName in basicTypes) KnitClassifier(returnTypeName)
        else KnitClassifier("L$returnTypeName;")
        return copy(classifier = classifier)
    }

    fun forceWrapped(): KnitType {
        val basicTypeIndex = basicTypes.indexOf(classifier.desc)
        if (basicTypeIndex == -1) return this
        val wrappedClassifier = KnitClassifier("L${basicTypeWrappers[basicTypeIndex]};")
        return copy(classifier = wrappedClassifier)
    }

    override fun toString(): String = Printer.type(this)

    companion object {
        private const val arrayClassifier = "Lkotlin/Array;"

        fun from(
            internalName: InternalName
        ): KnitType {
            val classifier = KnitClassifier.from(internalName)
            return from(classifier)
        }

        fun from(
            classifier: KnitClassifier, nullable: Boolean = false, named: String = "",
            typeParams: List<KnitGenericType> = listOf(),
        ): KnitType {
            val firstTypeParam = typeParams.firstOrNull()?.type
            if (classifier.desc == arrayClassifier && firstTypeParam != null) {
                // we do this, because kotlin array will not transform type to `[` when use kotlin class mapper lite
                val firstTypeClassifier = firstTypeParam.classifier
                return from(
                    KnitClassifier.fromArray(firstTypeClassifier), nullable, named,
                )
            }
            return KnitType(classifier, nullable, named, typeParams)
        }

        fun fromClass(
            container: MetadataContainer, classNode: ClassNode,
        ): KnitType {
            val classifier = KnitClassifier.from(container.name)
            val named = getNamedFromAnnotationNodes(classNode.allAnnotations).orEmpty()
            val idMapper = container.typeParameters.toIdMapper()
            val typeParams = container.typeParameters.map {
                KnitGenericType.fromTypeParam(it, idMapper)
            }
            return from(classifier, false, named, typeParams)
        }

        fun fromKmType(
            kmType: KmType, idMapper: TypeParamIdMapper, named: String = "",
        ): KnitType {
            val classifier = kmType.classifier
            val arguments = kmType.arguments
            val annotations = kmType.annotations
            val namedName = named.ifEmpty {
                getNamedFromAnnotations(annotations).orEmpty()
            }
            val originType = KnitClassifier.from(classifier)
            val typeParams = arguments.map {
                KnitGenericType.fromTypeProjection(it, idMapper)
            }
            val nullable = kmType.isNullable
            return from(originType, nullable, namedName, typeParams)
        }


        fun getNamedFromAnnotations(annotations: List<KmAnnotation>): String? {
            // infer name through annotations
            val namedAnnotationArgs = annotations.firstOrNull {
                it.className.toJvmInternalName() == namedInternalName
            } ?: return null
            return readNamedAnnotationFromKm(namedAnnotationArgs)
        }

        private fun getNamedFromAnnotationNodes(annotationNodes: List<AnnotationNode>): String? {
            // infer name through annotations
            val namedAnnotationArgs = annotationNodes.firstOrNull {
                it.desc == namedDesc
            } ?: return null
            return readNamedAnnotationFromNode(namedAnnotationArgs)
        }
    }
}

private fun readNamedAnnotationFromKm(namedAnnotation: KmAnnotation): String? {
    val arguments = namedAnnotation.arguments
    // use value first
    var value = arguments["value"]
    if (value is KmAnnotationArgument.StringValue) {
        val rawValue = value.value
        if (rawValue.isNotBlank()) return rawValue
    }
    // use qualifier next
    value = arguments["qualifier"]
    if (value is KmAnnotationArgument.KClassValue) {
        val className = value.className
        if (className.isNotBlank()) return className
    }
    return null
}

private fun readNamedAnnotationFromNode(annotationNode: AnnotationNode): String? {
    var value: String? = null
    var qualifier: InternalName? = null
    annotationNode.onEach { attrName, v ->
        when (attrName) {
            Named::value.callName -> value = v.cast()
            Named::qualifier.callName -> qualifier = v.readClassName()
        }
    }
    return qualifier ?: value
}

inline fun <reified T> Any.cast(): T = this as T
private fun Any.readClassName(): InternalName = (this as Type).internalName
