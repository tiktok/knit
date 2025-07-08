// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import knit.Component
import knit.Provides
import knit.Singleton
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.PropAccName
import tiktok.knit.plugin.allAnnotations
import tiktok.knit.plugin.annotated
import tiktok.knit.plugin.componentDesc
import tiktok.knit.plugin.diMutStubDesc
import tiktok.knit.plugin.diStubDesc
import tiktok.knit.plugin.firstOrNull
import tiktok.knit.plugin.ignoreInjectionDesc
import tiktok.knit.plugin.illegalState
import tiktok.knit.plugin.invoke
import tiktok.knit.plugin.isPublic
import tiktok.knit.plugin.knitVMAnnotationDesc
import tiktok.knit.plugin.providesDesc
import tiktok.knit.plugin.singletonDesc
import tiktok.knit.plugin.toFieldAccess
import kotlin.metadata.Visibility
import kotlin.metadata.isSecondary
import kotlin.metadata.jvm.signature
import kotlin.metadata.visibility

/**
 * Not necessarily only classes annotated with @[Component] are considered components. Here, it's more like a "component-like" concept. 
 * As long as a class has elements such as provides, compositeComponents, and injectedGetters, it can be regarded as one.
 * @param internalName internal name for this class, such as: java/lang/Object$XXX, see [InternalName]
 * @param provides all @[Provides] targeted function/property.
 * @param typeParams type parameter, like T in this: interface Comparable<T: Comparable>
 */
data class ComponentClass(
    val internalName: InternalName,
    val parents: List<CompositeComponent>, // parent internal name
    val typeParams: List<KnitGenericType>,
    val provides: MutableList<ProvidesMethod>,
    val compositeComponents: Map<PropAccName, CompositeComponent>, // property access name -> component
    val injectedGetters: List<InjectedGetter>,
    val singletons: List<KnitSingleton>,
    val isInterface: Boolean,
) {
    /** normal class (non-component) constructor, only uses its inheritors */
    constructor(
        internalName: InternalName, parents: List<CompositeComponent>,
    ) : this(
        internalName, parents, emptyList(), mutableListOf(),
        emptyMap(), emptyList(), emptyList(), false,
    )

    companion object {
        fun from(container: MetadataContainer): ComponentClass {
            val classNode = container.node
            val internalName: InternalName = container.name
            val isInterface = Opcodes.ACC_INTERFACE(classNode.access)

            val originTypeParams = container.typeParameters
            val idMapper: TypeParamIdMapper = originTypeParams.toIdMapper()
            val typeParams = originTypeParams.map {
                KnitGenericType.fromTypeParam(it, idMapper, needVerify = false)
            }
            // we regard super type as a special composite(actually it is extends)
            val parents = container.supertypes.map {
                KnitType.fromKmType(it, idMapper, needVerify = false)
            }.map { CompositeComponent(it) }

            val allConstructors = container.constructors
            val allProperties = container.properties
            val allFunctions = container.functions

            val provides = arrayListOf<ProvidesMethod>()
            val injectedGetters = arrayListOf<InjectedGetter>()
            val singletons = arrayListOf<KnitSingleton>()
            val compositeComponents = hashMapOf<PropAccName, CompositeComponent>()

            val componentAnnotations = classNode.allAnnotations
            val providesInfo = AnnotationReader.getProvidesAnnotationInfo(componentAnnotations)
            var mainProceed = false
            if (componentAnnotations.annotated(providesDesc)) {
                val classType by lazy {
                    KnitType.fromClass(container, classNode, needVerify = true)
                }
                val mainConstructor = container.constructors.firstOrNull {
                    // only process main
                    if (it.isSecondary) return@firstOrNull false
                    // not private
                    it.visibility == Visibility.PUBLIC || it.visibility == Visibility.INTERNAL
                }
                    ?: illegalState("no main constructor for $internalName or this is a private constructor.")
                val methodNode = container.getMethodNode(mainConstructor)
                componentAnnotations.readSingletonFromConstructor(singletons, methodNode, classType)
                // adds constructor
                provides += ProvidesMethod.fromConstructor(
                    container, classType, idMapper, mainConstructor, methodNode, providesInfo,
                )
                mainProceed = true
            }

            for (constructor in allConstructors) {
                val annotations = container.getConstructorAnnotations(constructor)
                val isProvides = annotations.annotated(providesDesc)
                val isVMProvides = annotations.annotated(knitVMAnnotationDesc)
                if (!isProvides && !isVMProvides) continue
                val isMainConstructor = !constructor.isSecondary
                // mainProceed, so skip main constructor
                if (mainProceed && isMainConstructor) continue
                val classType = KnitType.fromClass(container, classNode, needVerify = true)
                val methodNode = container.getMethodNode(constructor)
                // not private
                require(methodNode.isPublic) {
                    "${constructor.signature} in $internalName must not be private."
                }
                componentAnnotations.readSingletonFromConstructor(singletons, methodNode, classType)

                @Suppress("KotlinConstantConditions")
                if (isProvides) {
                    // normal constructor
                    provides += ProvidesMethod.fromConstructor(
                        container, classType, idMapper, constructor, methodNode,
                        if (isMainConstructor) providesInfo else null,
                    )
                } else if (isVMProvides) {
                    // vm constructor
                    provides += ProvidesMethod.fromVMConstructor(
                        container, classType, idMapper, constructor, methodNode,
                        if (isMainConstructor) providesInfo else null,
                    )
                }
            }

            for (property in allProperties) {
                val annotations = container.getPropertyAnnotations(property)
                val propertyType by lazy {
                    KnitType.fromKmType(property.returnType, idMapper, needVerify = true)
                }
                // read all provides annotations
                if (annotations.any { it.desc == providesDesc }) {
                    provides += ProvidesMethod.fromPropertyGetter(
                        container, property, propertyType,
                    )
                }
                // read all components
                if (annotations.any { it.desc == componentDesc }) {
                    val getter = container.getPropGetterOrNull(property)
                    require(!propertyType.nullable) {
                        // todo, maybe we can make it nullable?
                        "component ${property.name} in $internalName cannot nullable"
                    }
                    val access = getter?.name ?: property.name.toFieldAccess()
                    val isPublic = getter?.isPublic ?: (property.visibility == Visibility.PUBLIC)
                    compositeComponents[access] = CompositeComponent(propertyType, isPublic)
                }
                // read by di through delegate
                val delegate = "${property.name}\$delegate"
                val delegatedField =
                    container.getFieldNodeOrNull(delegate) ?: continue // no delegates
                // read all singleton annotations
                val singletonAnnotation = annotations.singletonAnnotation()
                if (singletonAnnotation?.isSingleton == true) {
                    val getter = container.getPropGetter(property)
                    singletons += KnitSingleton.from(
                        getter, propertyType, singletonAnnotation.threadsafe,
                    )
                }
                // ignore injection if annotated.
                if (!annotations.annotated(ignoreInjectionDesc)) when (delegatedField.desc) {
                    diMutStubDesc -> injectedGetters += InjectedGetter.from(
                        internalName, container.getPropGetter(property).name, propertyType,
                    )

                    diStubDesc -> {
                        val getter = container.getPropGetter(property)
                        // delegates by di stub
                        injectedGetters += InjectedGetter.from(internalName, getter.name, propertyType)
                        if (singletonAnnotation == null) {
                            // injected property is singleton by default
                            singletons += KnitSingleton.from(getter, propertyType, true)
                        }
                    }
                }
            }

            for (function in allFunctions) {
                val annotations = container.getFunctionAnnotations(function)
                val idMapperForMethod = idMapper + function.typeParameters
                val returnType by lazy {
                    KnitType.fromKmType(function.returnType, idMapperForMethod, needVerify = true)
                }
                // read all provides annotations
                if (annotations.any { it.desc == providesDesc }) {
                    provides += ProvidesMethod.fromFunction(
                        container, function, returnType, idMapperForMethod,
                    )
                }
                // read all singleton annotations
                val singletonAnnotation = annotations.singletonAnnotation()
                if (true == singletonAnnotation?.isSingleton) {
                    val methodNode = container.getMethodNode(function)
                    singletons += KnitSingleton.from(
                        methodNode, returnType, singletonAnnotation.threadsafe,
                    )
                }
            }

            if (isInterface && singletons.isNotEmpty()) {
                val singletonString = singletons.joinToString(prefix = "[", postfix = "]") {
                    it.funcName
                }
                illegalState("interface [$internalName] couldn't has any @Singleton, current singletons: $singletonString")
            }

            return ComponentClass(
                internalName, parents, typeParams, provides,
                compositeComponents, injectedGetters, singletons, isInterface,
            )
        }


    }
}

private fun List<AnnotationNode>.singletonAnnotation(): Singleton? {
    for (annotationNode in this) {
        val singleton = AnnotationReader.readSingleton(annotationNode)
        if (singleton != null) return singleton
    }
    return null
}

private fun List<AnnotationNode>.readSingletonFromConstructor(
    singletons: ArrayList<KnitSingleton>,
    constructorNode: MethodNode,
    classType: KnitType,
) {
    val singletonAnnotationNode = firstOrNull(singletonDesc)
        ?: constructorNode.allAnnotations.firstOrNull(singletonDesc)
    val singletonAnnotation = AnnotationReader.readSingleton(singletonAnnotationNode)
    // @Singleton at component class
    if (true == singletonAnnotation?.isSingleton) {
        singletons += KnitSingleton.fromConstructor(
            constructorNode, classType, singletonAnnotation.threadsafe,
        )
    }
}
