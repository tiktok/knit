// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.aload
import tiktok.knit.plugin.areturn
import tiktok.knit.plugin.astore
import tiktok.knit.plugin.athrow
import tiktok.knit.plugin.basicTypeIndex
import tiktok.knit.plugin.dup
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.getStatic
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.ifNotNull
import tiktok.knit.plugin.invokeSpecial
import tiktok.knit.plugin.invokeStatic
import tiktok.knit.plugin.jmp
import tiktok.knit.plugin.ldc
import tiktok.knit.plugin.monitorIn
import tiktok.knit.plugin.monitorOut
import tiktok.knit.plugin.new
import tiktok.knit.plugin.putStatic
import tiktok.knit.plugin.typedLoad
import tiktok.knit.plugin.unaryPlus

private const val FIELD_ACCESS = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC
private const val THREAD_SAFE_FIELD_ACCESS =
    Opcodes.ACC_PRIVATE or Opcodes.ACC_VOLATILE or Opcodes.ACC_STATIC
private const val METHOD_ACCESS = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC

/**
 * Created by yuejunyu on 2023/6/15
 * @author yuejunyu.0
 */
class GlobalProvidesWriter(private val context: KnitContext) {
    private val globalInjectionContainer by lazy { context.globalInjectionContainer }
    private val allMethods by lazy { globalInjectionContainer.all + globalInjectionContainer.allVM }
    private val singletons by lazy { globalInjectionContainer.allSingletons }

    fun write(node: ClassNode) {
        require(node.name == globalProvidesInternalName) {
            "GlobalSingletonWriter's node name must be $globalProvidesInternalName, actual: ${node.name}"
        }
        for ((_, providesMethod) in allMethods) {
            val containerName: InternalName = providesMethod.containerClass
            var providesMethodId = providesMethod.globalBytecodeIdentifier()
            // method desc is used for generated function desc
            val methodDesc = if (providesMethod.isConstructorLike()) {
                val newMethod = providesMethod.copy(functionName = "<init>")
                providesMethodId = newMethod.globalBytecodeIdentifier()
                newMethod.descWithReturnType()
            } else {
                providesMethod.desc
            }
            val singletonList = singletons[containerName]
            val singleton = singletonList?.firstOrNull {
                it.funcName == providesMethod.functionName &&
                    it.desc == providesMethod.desc
            }
            if (singleton != null) providesGlobalSingleton(
                providesMethod, node, providesMethodId, methodDesc, containerName, singleton,
            ) else {
                providesGlobal(providesMethodId, methodDesc, providesMethod, node, containerName)
            }
        }
    }

    private fun providesGlobal(
        providesMethodId: String,
        methodDesc: String,
        providesMethod: ProvidesMethod,
        node: ClassNode,
        containerName: InternalName
    ) {
        val methodNode = MethodNode(
            METHOD_ACCESS, providesMethodId,
            methodDesc, null, emptyArray(),
        )
        val argCount = providesMethod.requirements.size
        node.methods.add(methodNode)
        methodNode.instructions = InsnList().apply {
            buildObjectAsNormal(providesMethod, containerName, argCount)
            // return result
            areturn()
        }
    }

    private fun providesGlobalSingleton(
        providesMethod: ProvidesMethod,
        node: ClassNode,
        providesMethodId: String,
        methodDesc: String,
        containerName: InternalName,
        singleton: KnitSingleton
    ) {
        val typeDesc = providesMethod.actualType.descName
        val lockType = Type.getType(typeDesc)
        val access = if (singleton.threadSafe) THREAD_SAFE_FIELD_ACCESS else FIELD_ACCESS
        node.fields.add(
            FieldNode(access, providesMethodId, typeDesc, null, null),
        )
        val methodNode = MethodNode(
            METHOD_ACCESS, providesMethodId,
            methodDesc, null, emptyArray(),
        )
        val argCount = providesMethod.requirements.size
        node.methods.add(methodNode)
        methodNode.instructions = InsnList().apply {
            fun getFieldAndLoad() {
                getStatic(node.name, providesMethodId, typeDesc)
                astore(argCount)
                aload(argCount)
            }

            fun realInit() {
                buildObjectAsNormal(providesMethod, containerName, argCount)
                astore(argCount) // result = xxx

                // store cache, Global.backingField = result
                aload(argCount)
                putStatic(node.name, providesMethodId, typeDesc)
            }

            getFieldAndLoad()

            val endLabel = LabelNode()
            // if backing field non-null
            ifNotNull(endLabel)

            if (singleton.threadSafe) {
                val tryStartNode = LabelNode()
                val tryEndNode = LabelNode()
                val finallyNode = LabelNode()

                methodNode.tryCatchBlocks = listOf(
                    TryCatchBlockNode(
                        tryStartNode, tryEndNode, finallyNode, null,
                    ),
                )

                val innerIfLabel = LabelNode()
                ldc(lockType)
                monitorIn()

                +tryStartNode // try {
                getFieldAndLoad()
                ifNotNull(innerIfLabel)

                // normal logic
                +LabelNode()
                realInit()
                ldc(lockType)
                monitorOut()
                +tryEndNode // } catch
                jmp(endLabel)

                +finallyNode // catch {
                astore(argCount + 1)
                ldc(lockType)
                monitorOut()
                aload(argCount + 1)
                athrow()

                // backing field non-null, synchronized end
                +innerIfLabel
                ldc(lockType)
                monitorOut()
            } else {
                realInit()
            }

            // return <non-null> value
            +endLabel
            aload(argCount)
            areturn()
        }
    }

    private fun InsnList.buildObjectAsNormal(
        providesMethod: ProvidesMethod,
        containerName: InternalName,
        argCount: Int
    ) {
        val isConstructor = providesMethod.isConstructorLike()
        if (isConstructor) {
            new(containerName)
            dup()
        }
        val desc = providesMethod.desc
        val methodType = Type.getMethodType(desc)
        val args = methodType.argumentTypes

        var appendedIndices = 0
        for (i in 0 until argCount) {
            val argType = args[i]
            val argTypeIndex = argType.basicTypeIndex()
            // load args
            typedLoad(i + appendedIndices, argTypeIndex)
            when (argTypeIndex) {
                3, 5 -> appendedIndices++
            }
        }
        if (isConstructor) {
            invokeSpecial(containerName, "<init>", providesMethod.desc)
        } else {
            invokeStatic(containerName, providesMethod.functionName, providesMethod.desc)
        }
    }
}
