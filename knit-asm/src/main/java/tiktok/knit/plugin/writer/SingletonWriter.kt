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
import tiktok.knit.plugin.DescName
import tiktok.knit.plugin.FuncSignature
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.allAnnotations
import tiktok.knit.plugin.aload
import tiktok.knit.plugin.appendFrameWithType
import tiktok.knit.plugin.areturn
import tiktok.knit.plugin.astore
import tiktok.knit.plugin.athrow
import tiktok.knit.plugin.basicTypeIndex
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.getField
import tiktok.knit.plugin.ifNotNull
import tiktok.knit.plugin.invokeSpecial
import tiktok.knit.plugin.jmp
import tiktok.knit.plugin.monitorIn
import tiktok.knit.plugin.monitorOut
import tiktok.knit.plugin.putField
import tiktok.knit.plugin.sameFrame
import tiktok.knit.plugin.sameFrame1Throwable
import tiktok.knit.plugin.typedReturn
import tiktok.knit.plugin.unaryPlus
import tiktok.knit.plugin.unbox

private const val FIELD_ACCESS = Opcodes.ACC_PRIVATE
private const val THREAD_SAFE_FIELD_ACCESS = Opcodes.ACC_PRIVATE or Opcodes.ACC_VOLATILE

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 * @see [writeSingletonAndReturn]
 */
fun writeSingletonBackingFields(
    classNode: ClassNode, singletonMap: Map<FuncSignature, KnitSingleton>,
) {
    for ((signature, singleton) in singletonMap) {
        val backingName = signature.identifier
        val typeDesc = singleton.type.classifier.desc
        val access = if (singleton.threadSafe) THREAD_SAFE_FIELD_ACCESS else FIELD_ACCESS
        classNode.fields.add(
            FieldNode(
                access, backingName, typeDesc, null, null,
            ),
        )
    }
}

fun InsnList.writeSingletonInjection(
    className: InternalName,
    singleton: KnitSingleton,
    localPropId: Int,
    injectedType: KnitType,
    buildObject: InsnList.() -> Unit,
): List<TryCatchBlockNode> {
    // has singleton, we should cache it
    val propName = FuncSignature.from(singleton).identifier
    val typeDesc = singleton.type.classifier.desc
    val basicTypeIndex = injectedType.basicTypeIndex()

    return writeSingletonAndReturn(
        className, propName, typeDesc, singleton.threadSafe,
        localPropId, basicTypeIndex, buildObject,
    )
}

/**
 * write singleton logic by double-checked null judgement
 *
 * @param propName property which holds cache
 * @param propTypeDesc property type which holds cache
 * @param threadSafe use lock to initialize if threadSafe
 * @param localPropId local variable id for store object in stack, must be the last index of stack table!
 * @param unboxId unbox if needed, see also: [basicTypeIndex]
 * @param buildObject real logic for build needed object
 */
fun InsnList.writeSingletonAndReturn(
    className: InternalName,
    propName: String,
    propTypeDesc: DescName,
    threadSafe: Boolean,
    localPropId: Int,
    unboxId: Int,
    buildObject: InsnList.() -> Unit,
): List<TryCatchBlockNode> {
    fun getFieldAndLoad() {
        aload(0)
        getField(className, propName, propTypeDesc)
        astore(localPropId)
        aload(localPropId)
    }

    fun initAndSetProp() {
        buildObject()
        astore(localPropId) // result = xxx
        // this.backingField = result
        aload(0)
        aload(localPropId)
        putField(className, propName, propTypeDesc)
    }

    getFieldAndLoad()

    val returnLabel = LabelNode()
    // if backing field non-null
    ifNotNull(returnLabel)

    val tryCatchNodes = mutableListOf<TryCatchBlockNode>()

    val handlerNode = LabelNode()
    if (threadSafe) {
        val tryStartNode = LabelNode()
        val tryEndNode = LabelNode()
        val innerIfLabel = LabelNode()

        tryCatchNodes += TryCatchBlockNode(
            tryStartNode, tryEndNode, handlerNode, null,
        )

        // synchronized(this) {
        aload(0)
        monitorIn()
        // get field to index again
        +tryStartNode
        getFieldAndLoad()
        ifNotNull(innerIfLabel)

        initAndSetProp()
        // synchronized end
        aload(0)
        monitorOut()
        +tryEndNode
        jmp(returnLabel)

        // backing field non-null, synchronized end
        +innerIfLabel
        // FRAME: add temp var
        appendFrameWithType(Type.getType(propTypeDesc).internalName)
        aload(0)
        monitorOut()
    } else {
        initAndSetProp()
    }

    +returnLabel
    sameFrame()
    aload(localPropId)
    if (unboxId == -1) areturn() else {
        unbox(unboxId)
        typedReturn(unboxId)
    }

    if (threadSafe) {
        // error handle for synchronized
        +handlerNode
        sameFrame1Throwable()
        astore(localPropId + 1)
        aload(0)
        monitorOut()
        aload(localPropId + 1)
        athrow()
    }
    return tryCatchNodes
}

fun writeExistedProvidesSingleton(
    classNode: ClassNode, methodNode: MethodNode, singleton: KnitSingleton,
) {
    val delegateName = "${methodNode.name}\$singleton\$gen"
    val methodDesc = methodNode.desc
    val delegateMethod = MethodNode(
        Opcodes.ACC_PRIVATE, delegateName,
        methodDesc, null, emptyArray(),
    )
    delegateMethod.instructions = methodNode.instructions
    classNode.methods.add(delegateMethod)

    val newMethod = MethodNode(
        methodNode.access, methodNode.name,
        methodDesc, null, emptyArray(),
    ).apply {
        invisibleAnnotations = methodNode.allAnnotations
        visibleAnnotations = methodNode.visibleAnnotations
    }
    classNode.methods.remove(methodNode)
    classNode.methods.add(newMethod)
    val methodDescType = Type.getMethodType(methodDesc)
    val argCount = methodDescType.argumentTypes.size
    val newInsn = InsnList()
    newMethod.instructions = newInsn
    val cacheStoreIdx = argCount + 1

    val methodKnitType = KnitType.from(methodDescType.returnType.internalName)
    newInsn.writeSingletonInjection(
        classNode.name, singleton, cacheStoreIdx, methodKnitType,
    ) {
        // load args
        for (i in 0..argCount) aload(i)
        invokeSpecial(classNode.name, delegateName, delegateMethod.desc)
    }
    methodNode.instructions = newInsn
}
