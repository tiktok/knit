// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

@file:Suppress("SpellCheckingInspection")

package tiktok.knit.plugin

import knit.Factory
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import tiktok.knit.plugin.element.KnitType
import java.lang.invoke.LambdaMetafactory
import kotlin.reflect.jvm.javaMethod

/**
 * Created by yuejunyu on 2023/6/15
 * @author yuejunyu.0
 */
fun InsnList.aload(idx: Int) = +VarInsnNode(Opcodes.ALOAD, idx)
fun InsnList.athrow() = +InsnNode(Opcodes.ATHROW)
fun InsnList.areturn() = +InsnNode(Opcodes.ARETURN)
fun InsnList.astore(idx: Int) = +VarInsnNode(Opcodes.ASTORE, idx)
fun InsnList.istore(idx: Int) = +VarInsnNode(Opcodes.ISTORE, idx)
fun InsnList.iload(idx: Int) = +VarInsnNode(Opcodes.ILOAD, idx)
fun InsnList.returnUnit() = +InsnNode(Opcodes.RETURN)

fun InsnList.invokeVirtual(
    owner: InternalName, name: FuncName, descriptor: FuncDesc,
) = +MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, descriptor)

fun InsnList.invokeStatic(
    owner: InternalName, name: FuncName, descriptor: FuncDesc,
) = +MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, descriptor)

fun InsnList.invokeSpecial(
    owner: InternalName, name: FuncName, descriptor: FuncDesc,
) = +MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, descriptor)

fun InsnList.invokeInterface(
    owner: InternalName, name: FuncName, descriptor: FuncDesc,
) = +MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, descriptor)

fun InsnList.checkCast(
    internalName: InternalName,
) = +TypeInsnNode(Opcodes.CHECKCAST, internalName)

fun InsnList.getField(
    owner: InternalName, name: String, descriptor: DescName,
) = +FieldInsnNode(Opcodes.GETFIELD, owner, name, descriptor)

fun InsnList.getStatic(
    owner: InternalName, name: String, descriptor: DescName,
) = +FieldInsnNode(Opcodes.GETSTATIC, owner, name, descriptor)

fun InsnList.putField(
    owner: InternalName, name: String, descriptor: DescName,
) = +FieldInsnNode(Opcodes.PUTFIELD, owner, name, descriptor)

fun InsnList.putStatic(
    owner: InternalName, name: String, descriptor: DescName,
) = +FieldInsnNode(Opcodes.PUTSTATIC, owner, name, descriptor)

fun InsnList.ifNotNull(
    skipTo: LabelNode,
) = +JumpInsnNode(Opcodes.IFNONNULL, skipTo)

fun InsnList.ifNull(
    skipTo: LabelNode,
) = +JumpInsnNode(Opcodes.IFNULL, skipTo)

fun InsnList.jmp(
    skipTo: LabelNode
) = +JumpInsnNode(Opcodes.GOTO, skipTo)

fun InsnList.new(
    classDesc: InternalName
) = +TypeInsnNode(Opcodes.NEW, classDesc)

fun InsnList.dup(
) = +InsnNode(Opcodes.DUP)

fun InsnList.monitorIn(
) = +InsnNode(Opcodes.MONITORENTER)

fun InsnList.monitorOut(
) = +InsnNode(Opcodes.MONITOREXIT)

fun InsnList.ldc(
    type: Type,
) = +LdcInsnNode(type)

fun InsnList.aconstNull(
) = +InsnNode(Opcodes.ACONST_NULL)

fun InsnList.ificmpne(
    labelNode: LabelNode
) = +JumpInsnNode(Opcodes.IF_ICMPNE, labelNode)

fun InsnList.ifeq(labelNode: LabelNode) = +JumpInsnNode(Opcodes.IFEQ, labelNode)

fun InsnList.ifacmpeq(
    labelNode: LabelNode
) = +JumpInsnNode(Opcodes.IF_ACMPEQ, labelNode)

fun InsnList.ifacmpne(
    labelNode: LabelNode
) = +JumpInsnNode(Opcodes.IF_ACMPNE, labelNode)

fun InsnList.instanceof(
    type: InternalName
) = +TypeInsnNode(Opcodes.INSTANCEOF, type)

fun InsnList.addLabel(
    labelNode: LabelNode
) = +labelNode

fun InsnList.goto(
    labelNode: LabelNode
) = +JumpInsnNode(Opcodes.GOTO, labelNode)

fun InsnList.pop() = +InsnNode(Opcodes.POP)

context(InsnList) operator fun AbstractInsnNode.unaryPlus() {
    add(this)
}

/**
 * This function is used to generate an InvokeDynamicInsnNode for ASM.
 *
 * For instance, given this interface:
 * ```java
 * interface Factory {
 *     String s()
 * }
 * ```
 * generated method inside of Foo should look like this:
 * ```java
 * class Foo {
 *     static String generatedString(Foo foo)
 * }
 * ```
 *
 * @param inWhichClass The class that contains this lambda function
 * @param invokeDynamicName name of the function you wish to invoke (e.g., `s`)
 * @param invokeDynamicDesc method descriptor representing the signature of the dynamic method call.
 *                          (e.g., `(Foo) -> Factory` becomes `(Lexample/Foo;)Lexample/Factory;`)
 * @param lambdaGeneratedMethodName name of the static generated delegate method  (e.g., `generatedString`)
 * @param lambdaGeneratedMethodDesc descriptor of the static generated delegate method (e.g., `(Lexample/Foo;)Ljava/lang/String;`)
 * @param delegateMethodDesc descriptor of the delegate method (e.g., `()Ljava/lang/String;`)
 * @param erasedDelegateMethodDesc same as [delegateMethodDesc] but erased.
 */
fun InsnList.invokeDynamic(
    inWhichClass: InternalName,
    invokeDynamicName: FuncName, invokeDynamicDesc: FuncDesc,
    lambdaGeneratedMethodName: FuncName, lambdaGeneratedMethodDesc: FuncDesc,
    delegateMethodDesc: FuncDesc, erasedDelegateMethodDesc: FuncDesc,
) {
    val lambdaGeneratedMethodHandle = Handle(
        Opcodes.H_INVOKESTATIC, inWhichClass, lambdaGeneratedMethodName, lambdaGeneratedMethodDesc, false,
    )
    val invokeDynamicNode = InvokeDynamicInsnNode(
        invokeDynamicName, invokeDynamicDesc, lambdaFactoryHandle,
        erasedDelegateMethodDesc.asType(), lambdaGeneratedMethodHandle, delegateMethodDesc.asType(),
    )
    add(invokeDynamicNode)
}

private fun FuncDesc.asType(): Type = Type.getMethodType(this)

private val metafactoryMethod = requireNotNull(LambdaMetafactory::metafactory.javaMethod)
private val metafactoryDesc = Type.getMethodDescriptor(metafactoryMethod)
private val lambdaFactoryHandle = Handle(
    Opcodes.H_INVOKESTATIC, LambdaMetafactory::class.internalName,
    metafactoryMethod.name, metafactoryDesc, false,
)

/** this is type desc of [Factory], but type arg has been erased as [Object]  */
const val FACTORY_LAMBDA_ERASED_DESC = "()Ljava/lang/Object;"

fun InsnList.unbox(typeIndex: Int) {
    val wrapName: InternalName = basicTypeWrappers[typeIndex]
    val basicTypeDesc: DescName = basicTypes[typeIndex]
    val unboxFunction = unboxFunctions[typeIndex]
    invokeVirtual(wrapName, unboxFunction, "()$basicTypeDesc")
}

fun InsnList.box(typeIndex: Int) {
    val wrapName: InternalName = basicTypeWrappers[typeIndex]
    val basicTypeDesc: DescName = basicTypes[typeIndex]
    invokeStatic(wrapName, "valueOf", "($basicTypeDesc)L$wrapName;")
}

fun InsnList.typedReturn(typeIndex: Int) {
    if (typeIndex == -1) areturn() else when (typeIndex) {
        0, 1, 2, 6, 7 -> +InsnNode(Opcodes.IRETURN)
        3 -> +InsnNode(Opcodes.LRETURN)
        4 -> +InsnNode(Opcodes.FRETURN)
        5 -> +InsnNode(Opcodes.DRETURN)
    }
}

fun InsnList.typedLoad(index: Int, typeIndex: Int) {
    if (typeIndex == -1) aload(index) else when (typeIndex) {
        0, 1, 2, 6, 7 -> +VarInsnNode(Opcodes.ILOAD, index)
        3 -> +VarInsnNode(Opcodes.LLOAD, index)
        4 -> +VarInsnNode(Opcodes.FLOAD, index)
        5 -> +VarInsnNode(Opcodes.DLOAD, index)
    }
}

fun KnitType.basicTypeIndex(): Int {
    if (classifier.isTypeParameter()) return -1
    val classifierDesc = classifier.desc
    return basicTypes.indexOf(classifierDesc)
}

fun Type.basicTypeIndex(): Int {
    return basicTypes.indexOf(descriptor)
}

fun InsnList.intConst(constInt: Int) {
    +when (constInt) {
        in -1..5 -> InsnNode(Opcodes.ICONST_0 + constInt)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, constInt)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, constInt)
        else -> LdcInsnNode(constInt)
    }
}

fun InsnList.strConst(stringConst: String) = +LdcInsnNode(stringConst)

fun InsnList.newArray(size: Int, internalName: InternalName) {
    require(size >= 0) { "need size >= 0" }
    intConst(size)
    +TypeInsnNode(Opcodes.ANEWARRAY, internalName)
}

inline fun InsnList.pushToArray(index: Int, produceObject: InsnList.() -> Unit) {
    dup()
    intConst(index)
    produceObject()
    +InsnNode(Opcodes.AASTORE)
}

fun InsnList.newArrayList(index: Int, init: (InsnList.() -> Unit)? = null) {
    val listInternalName = ArrayList::class.internalName
    new(listInternalName)
    dup()
    if (init == null) {
        invokeSpecial(listInternalName, "<init>", "()V")
    } else {
        init()
    }
    astore(index)
}

fun InsnList.addToArrayList(index: Int, produceObject: InsnList.() -> Unit) {
    aload(index)
    produceObject()
    invokeVirtual(ArrayList::class.internalName, "add", "(Ljava/lang/Object;)Z")
    pop()
}

fun InsnList.invokeVirtualOrInterface(
    owner: InternalName, name: FuncName, descriptor: FuncDesc, isInterface: Boolean
) {
    if (isInterface) invokeInterface(owner, name, descriptor)
    else invokeVirtual(owner, name, descriptor)
}


typealias CaseBranch = InsnList.(Int) -> Unit

fun InsnList.lookupSwitch(
    cases: Collection<Int>,
    defaultCaseLabel: LabelNode,
    eachCase: CaseBranch,
) {
    val sortedCases = cases.sorted()
    val labels = Array(sortedCases.size) { LabelNode() }

    +LookupSwitchInsnNode(defaultCaseLabel, sortedCases.toIntArray(), labels)

    sortedCases.forEachIndexed { index, caseInt ->
        +labels[index]
        eachCase(caseInt)
    }
}
