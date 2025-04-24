// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import knit.Factory
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.aload
import tiktok.knit.plugin.areturn
import tiktok.knit.plugin.asMetadataContainer
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.element.toIdMapper
import tiktok.knit.plugin.exactSingleInjection
import tiktok.knit.plugin.function0Desc
import tiktok.knit.plugin.illegalState
import tiktok.knit.plugin.injection.CPF
import tiktok.knit.plugin.injection.FindInjectionContext
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.injection.InjectionBinder
import tiktok.knit.plugin.injection.InjectionFactoryContext
import tiktok.knit.plugin.invokeSpecial
import tiktok.knit.plugin.invokeStatic
import tiktok.knit.plugin.knitVmFactoryImplName
import tiktok.knit.plugin.knitVmFactoryOwnerName
import tiktok.knit.plugin.ldc
import tiktok.knit.plugin.newArray
import tiktok.knit.plugin.objectInternalName
import tiktok.knit.plugin.pushToArray
import tiktok.knit.plugin.toInternalName
import kotlin.metadata.KmProperty
import kotlin.metadata.isDelegated

private const val FIELD_ACCESS = Opcodes.ACC_PRIVATE
private const val VM_GEN_METHOD_ACCESS = Opcodes.ACC_PRIVATE

private const val DEFAULT_VM_FACTORY_METHOD_NAME = "getDefaultViewModelProviderFactory"
private const val VM_FACTORY_DESC = "Landroidx/lifecycle/ViewModelProvider\$Factory;"


/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
class VMProperty(
    val property: KmProperty,
    val type: KnitType,
    val injection: Injection,
)

fun generateVMLogic(context: KnitContext, classNode: ClassNode, thisComponent: BoundComponentClass) {
    val inheritJudgement = context.inheritJudgement
    val isVmFactoryHolder = inheritJudgement.inherit(classNode.name, knitVmFactoryOwnerName)
    if (!isVmFactoryHolder) return
    val allVMProperties = getVMProperties(context, classNode, thisComponent)
    if (allVMProperties.isEmpty()) return

    val componentClass = context.componentMap[classNode.name]
    componentClass?.injectedVmTypes?.addAll(allVMProperties.map { it.type.internalName })

    // create vm prop provider functions
    for (vmProp in allVMProperties) {
        val backendFunctionName = vmProp.property.name + "\$knitVm"
        val methodNode = MethodNode(
            VM_GEN_METHOD_ACCESS, backendFunctionName,
            "()$function0Desc",
            null, emptyArray(),
        )
        val injectContext = InjectionWriterContext(
            thisComponent, classNode, vmProp.injection, methodNode, emptyMap(),
        )
        methodNode.instructions = InsnList().apply {
            DepsWriter.buildDeps(this, injectContext)
            areturn()
        }
        classNode.methods.add(methodNode)
    }

    val containsDefaultFactoryProviderMethod = classNode.methods.any {
        DEFAULT_VM_FACTORY_METHOD_NAME == it.name && it.desc.startsWith("()")
    }

    if (containsDefaultFactoryProviderMethod) illegalState(
        "cannot inject to HasDefaultViewModelProviderFactory.getDefaultViewModelProviderFactory for: ${thisComponent.internalName}\n" +
            "cause you have declared some ViewModels are injected by Knit: ${allVMProperties.joinToString { it.property.name }} but you have implement the getDefaultViewModelProviderFactory",
    )

    val vmProviderCacheProp = FieldNode(
        FIELD_ACCESS, "knit\$vmCache", VM_FACTORY_DESC, null, null,
    )
    classNode.fields.add(vmProviderCacheProp)

    // create vm provider factory
    val generatedVmFactoryMethod = MethodNode(
        Opcodes.ACC_PUBLIC, DEFAULT_VM_FACTORY_METHOD_NAME,
        "()$VM_FACTORY_DESC",
        null, emptyArray(),
    )
    generatedVmFactoryMethod.instructions = InsnList().apply {
        writeSingletonAndReturn(
            classNode.name, vmProviderCacheProp.name, VM_FACTORY_DESC,
            true, 1, -1,
        ) {
            newArray(allVMProperties.size * 2, objectInternalName)
            var currentIndex = 0
            for (vmProp in allVMProperties) {
                pushToArray(currentIndex) {
                    val type = Type.getType(vmProp.type.classifier.desc)
                    ldc(type)
                }
                pushToArray(currentIndex + 1) {
                    val backendFunctionName = vmProp.property.name + "\$knitVm"
                    aload(0)
                    invokeSpecial(classNode.name, backendFunctionName, "()$function0Desc")
                }
                currentIndex += 2
            }
            invokeStatic(
                knitVmFactoryImplName, "from",
                "([L$objectInternalName;)$VM_FACTORY_DESC",
            )
        }
    }
    classNode.methods.add(generatedVmFactoryMethod)
}

fun getVMProperties(
    context: KnitContext,
    classNode: ClassNode,
    thisComponent: BoundComponentClass,
): List<VMProperty> {
    val factoryContext = InjectionFactoryContext(context.inheritJudgement)
    val metadata = classNode.asMetadataContainer() ?: return emptyList()
    val properties = metadata.properties.filter {
        it.isDelegated
    }

    val vmProviders = context.globalInjectionContainer.vmProviders

    val requiredVmProviders = ArrayList<Pair<KmProperty, List<ProvidesMethod>>>()
    for (property in properties) {
        val typeName = KnitClassifier.from(property.returnType.classifier).desc.toInternalName()
        val providesMethods = vmProviders[typeName] ?: continue
        requiredVmProviders += property to providesMethods
    }

    if (requiredVmProviders.isEmpty()) return emptyList()
    val idMapper = metadata.typeParameters.toIdMapper()
    val thisAllProvides = CPF.all(thisComponent, true)
    val globalAllProvides = context.globalInjectionContainer.all
    val allRequirementsProvides = thisAllProvides + globalAllProvides

    val allVMInjections = arrayListOf<VMProperty>()
    for ((vmProp, providers) in requiredVmProviders) {
        val propType = KnitType.fromKmType(vmProp.returnType, idMapper)
        val propFactoryType = KnitType.from(
            KnitClassifier.from(Factory::class),
            typeParams = listOf(propType.toGeneric()),
        )
        val allProvides = allRequirementsProvides +
            providers.map { Injection.From.GLOBAL(it.copy(functionName = "<init>")) }
        val findingContext = FindInjectionContext(
            factoryContext, thisComponent, propFactoryType, allProvides, false,
        )
        val injections = InjectionBinder.buildInjectionFrom(findingContext)
        // must single
        val injection = injections.exactSingleInjection(thisComponent, propType) { providers }.getOrThrow()
        allVMInjections += VMProperty(vmProp, propType, injection)
    }
    return allVMInjections
}