// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.FuncDesc
import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.MultiBindingType
import tiktok.knit.plugin.Printer
import tiktok.knit.plugin.basicTypeIndex
import tiktok.knit.plugin.isPublic
import tiktok.knit.plugin.isStatic
import tiktok.knit.plugin.knitInternalError
import tiktok.knit.plugin.mbBuilderInternalName
import tiktok.knit.plugin.objectType
import kotlin.metadata.ExperimentalContextReceivers
import kotlin.metadata.KmConstructor
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.isSuspend


/**
 * @property containerClass it means which container provides it
 * @property providesTypes List<KnitType>
 * @property logName String
 * @property intoTarget IntoSet/List/Map
 * @property requirements value parameter of this constructor
 * @property onlyCollectionProvides for multibinding provider, it maybe not provides like 'normal' @Provides.
 */
data class ProvidesMethod(
    val containerClass: InternalName,
    val actualType: KnitType,
    val providesTypes: List<KnitType>,
    val requirements: List<KnitType>,
    val typeParams: List<KnitGenericType>,
    val desc: FuncDesc,
    val intoTarget: String,
    val functionName: FuncName,
    val staticProvides: Boolean,
    val interfaceProvides: Boolean,
    val isPublic: Boolean,
    val onlyCollectionProvides: Boolean,
    val priority: Int,
) {
    val logName: String
        get() = Printer.method(this)

    val identifier: String = "$containerClass $functionName $desc"

    // used for function name and variable name if it is a global @Provides.
    fun globalBytecodeIdentifier(): String {
        val name = (containerClass + functionName + desc)
            .replace('/', '_')
        return "gi_" + name.filter { it.isJavaIdentifierPart() }
    }

    // used for call a global @Provides function.
    // In global function real injection, arg type & return type will be erased.
    //
    // @Provides fun provideFoo(bar: Bar): Foo
    // → fun gi_provideFoo(bar: Any): Any
    fun globalCallDesc(): String {
        val type = Type.getMethodType(desc)
        val argumentTypes = type.argumentTypes
        val newArgumentType = Array(argumentTypes.size) { objectType }
        for ((i, argumentType) in argumentTypes.withIndex()) {
            // normal object, skip
            if (argumentType.basicTypeIndex() == -1) continue
            // basic type, keep type
            newArgumentType[i] = argumentType
        }
        return Type.getMethodDescriptor(objectType, *newArgumentType)
    }

    fun isConstructor() = functionName == "<init>"
    fun isConstructorLike() = isConstructor() || isVM()
    fun isLambda() = functionName == TAG_LAMBDA
    fun isMultiBinding() = containerClass == mbBuilderInternalName
    fun isVM() = functionName == TAG_VM_INIT

    companion object {
        fun from(
            containerClass: InternalName,
            desc: FuncDesc,
            functionName: FuncName,
            actualType: KnitType,
            providesTypes: List<KnitType> = listOf(actualType),
            requirements: List<KnitType> = emptyList(),
            typeParams: List<KnitGenericType> = emptyList(),
            intoTarget: String = "",
            staticProvides: Boolean = false,
            interfaceProvides: Boolean = false,
            isPublic: Boolean = true,
            onlyCollectionProvides: Boolean = false,
            priority: Int = 0,
        ): ProvidesMethod {
            if (staticProvides) require(isPublic) {
                "static @Provides element must be public: $containerClass.$functionName"
            }
            return ProvidesMethod(
                containerClass, actualType, providesTypes, requirements, typeParams,
                desc, intoTarget, functionName, staticProvides, interfaceProvides,
                isPublic, onlyCollectionProvides, priority
            )
        }

        private fun fromMethodNode(
            containerClassName: InternalName,
            methodNode: MethodNode,
            actualType: KnitType,
            onlyCollectionProvides: Boolean,
            providesTypes: List<KnitType>,
            intoTarget: String = "",
            functionName: FuncName = methodNode.name,
            requirements: List<KnitType> = emptyList(),
            typeParams: List<KnitGenericType> = emptyList(),
            interfaceProvides: Boolean = false,
            isPublic: Boolean = methodNode.isPublic,
            staticProvides: Boolean = methodNode.isStatic,
            priority: Int = 0,
        ): ProvidesMethod = from(
            containerClass = containerClassName,
            desc = methodNode.desc,
            functionName = functionName,
            actualType = actualType,
            providesTypes = providesTypes,
            requirements = requirements,
            typeParams = typeParams,
            intoTarget = intoTarget,
            staticProvides = staticProvides,
            interfaceProvides = interfaceProvides,
            isPublic = isPublic,
            onlyCollectionProvides = onlyCollectionProvides,
            priority = priority,
        )

        fun fromFunction(
            container: MetadataContainer, kmFunction: KmFunction,
            actualType: KnitType, methodTypeParamMapper: TypeParamIdMapper,
        ): ProvidesMethod {
            require(!kmFunction.isSuspend) {
                "suspend is not supported for injection: ${container.name}.${kmFunction.name}"
            }
            val methodNode = container.getMethodNode(kmFunction)
            val methodArgs = Type.getMethodType(methodNode.desc).argumentTypes

            var annotationInfo = AnnotationReader.getProvidesAnnotationInfo(methodNode)
            val actualTypeFixed = actualType.adaptBasicType(methodNode)
            annotationInfo = annotationInfo.copy(
                providesTypes = annotationInfo.providesTypes.ifEmpty { listOf(actualTypeFixed) },
            )

            @OptIn(ExperimentalContextReceivers::class)
            val requirementParameters: List<KmType> =
                kmFunction.contextReceiverTypes +
                    kmFunction.receiverParameterType?.let { listOf(it) }.orEmpty() +
                    kmFunction.valueParameters.map { it.type }

            if (methodArgs.size != requirementParameters.size) {
                knitInternalError("knit meets unexpected error when resolve ${container.name}.${kmFunction.name}, it has wrong arguments count in bytecode(${methodArgs.map { it.internalName }}) and kotlin metadata(${requirementParameters.map { it.classifier }}), ")
            }

            val requirements = ArrayList<KnitType>(methodArgs.size)
            for (i in methodArgs.indices) {
                val requirement = requirementParameters[i]
                val methodArg = methodArgs[i]
                val requirementType = KnitType
                    .fromKmType(requirement, methodTypeParamMapper, needVerify = true)
                    .adaptBasicType(methodArg)
                requirements += requirementType
            }

            val typeParams = kmFunction.typeParameters
            val typeParameters = typeParams.map {
                KnitGenericType.fromTypeParam(it, methodTypeParamMapper, needVerify = true)
            }

            return fromMethodNode(
                containerClassName = container.name,
                methodNode = methodNode,
                actualType = actualTypeFixed,
                requirements = requirements,
                typeParams = typeParameters,
                providesTypes = annotationInfo.providesTypes,
                intoTarget = annotationInfo.intoTarget,
                onlyCollectionProvides = annotationInfo.onlyCollectionProvides,
                interfaceProvides = container.isInterface,
                priority = annotationInfo.priority,
            )
        }

        fun fromPropertyGetter(
            container: MetadataContainer, property: KmProperty,
            actualType: KnitType,
        ): ProvidesMethod {
            val annotations = container.getPropertyAnnotations(property)
            var annotationInfo = AnnotationReader.getProvidesAnnotationInfo(annotations)
            val getterMethod = container.getPropGetter(property)
            val actualTypeFixed = actualType.adaptBasicType(getterMethod)
            annotationInfo = annotationInfo.copy(
                providesTypes = annotationInfo.providesTypes.ifEmpty { listOf(actualTypeFixed) },
            )
            return fromMethodNode(
                containerClassName = container.name,
                methodNode = getterMethod,
                actualType = actualTypeFixed,
                providesTypes = annotationInfo.providesTypes,
                intoTarget = annotationInfo.intoTarget,
                onlyCollectionProvides = annotationInfo.onlyCollectionProvides,
                interfaceProvides = container.isInterface,
                priority = annotationInfo.priority,
            )
        }

        fun fromConstructor(
            container: MetadataContainer,
            thisClassType: KnitType,
            parentMapper: TypeParamIdMapper,
            kmConstructor: KmConstructor,
            constructorNode: MethodNode,
            classProvidesInfo: ProvidesAnnotationInfo?,
        ): ProvidesMethod {
            return fromConstructorLike(
                container, thisClassType, parentMapper,
                kmConstructor, constructorNode, classProvidesInfo, "<init>",
            )
        }

        fun fromVMConstructor(
            container: MetadataContainer,
            thisClassType: KnitType,
            parentMapper: TypeParamIdMapper,
            kmConstructor: KmConstructor,
            constructorNode: MethodNode,
            classProvidesInfo: ProvidesAnnotationInfo?,
        ): ProvidesMethod {
            return fromConstructorLike(
                container, thisClassType, parentMapper,
                kmConstructor, constructorNode, classProvidesInfo, TAG_VM_INIT,
            )
        }

        private fun fromConstructorLike(
            container: MetadataContainer,
            thisClassType: KnitType,
            parentMapper: TypeParamIdMapper,
            kmConstructor: KmConstructor,
            constructorNode: MethodNode,
            classProvidesInfo: ProvidesAnnotationInfo?,
            functionName: FuncName,
        ): ProvidesMethod {
            val containerClass = container.name

            var annotationInfo = AnnotationReader.getProvidesAnnotationInfo(constructorNode)
            val providesTypes: List<KnitType>
            // use constructor provides first
            val constructorProvides = annotationInfo.providesTypes
            val classProvides = classProvidesInfo?.providesTypes
            providesTypes = when {
                constructorProvides.isNotEmpty() -> constructorProvides
                classProvides?.isNotEmpty() == true -> classProvides
                else -> listOf(thisClassType)
            }
            annotationInfo = annotationInfo.copy(providesTypes = providesTypes)

            val requirements = kmConstructor.valueParameters.map {
                KnitType.fromKmType(it.type, parentMapper, needVerify = true)
            }
            val isPublic = container.node.isPublic && constructorNode.isPublic

            val intoTarget = annotationInfo.intoTarget.ifEmpty {
                classProvidesInfo?.intoTarget.orEmpty()
            }

            val onlyCollectionProvides = annotationInfo.onlyCollectionProvides
                || classProvidesInfo?.onlyCollectionProvides == true
            return fromMethodNode(
                containerClassName = containerClass,
                methodNode = constructorNode,
                functionName = functionName,
                actualType = thisClassType,
                onlyCollectionProvides = onlyCollectionProvides,
                providesTypes = providesTypes,
                requirements = requirements,
                intoTarget = intoTarget,
                typeParams = emptyList(),
                isPublic = isPublic,
                staticProvides = true,
                interfaceProvides = false,
                priority = annotationInfo.priority,
            )
        }

        fun fromMultibinding(
            bindingType: MultiBindingType,
        ): ProvidesMethod {
            return from(
                containerClass = mbBuilderInternalName,
                desc = "([Ljava/lang/Object;)${bindingType.type.descName}",
                functionName = bindingType.functionName,
                actualType = bindingType.type,
                /** set [staticProvides] to false to avoid process by [GlobalProvidesWriter] */
                staticProvides = false,
            )
        }

        const val TAG_LAMBDA = "<lambda>"
        private const val TAG_VM_INIT = "<vm-init>"
    }
}
