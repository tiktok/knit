// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.writer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import tiktok.knit.plugin.FACTORY_LAMBDA_ERASED_DESC
import tiktok.knit.plugin.aload
import tiktok.knit.plugin.function0Desc
import tiktok.knit.plugin.invokeDynamic
import tiktok.knit.plugin.toObjDescName

private const val LAMBDA_GEN_ACCESS = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
fun InsnList.writeFactoryLambda(
    getterContext: InjectionWriterContext
) {
    val (inWhichComponent, classNode, injection, getterNode, _) = getterContext
    aload(0)
    val inWhichComponentName = inWhichComponent.internalName
    val inWhichComponentDesc = inWhichComponentName.toObjDescName()
    val lambdaInjection = injection.requirementInjections.first()
    val thisType = Type.getType(inWhichComponentDesc)
    val requiredType = Type.getType(lambdaInjection.type.descName)

    // lambda generated for invoke dynamic
    val lambdaGeneratedFunctionType = Type.getMethodType(requiredType, thisType)
    val lambdaGeneratedFunctionName = "knitL_${getterNode.name}${getterContext.lambdaIndex++}"
    val lambdaGeneratedFunction = MethodNode(
        LAMBDA_GEN_ACCESS, lambdaGeneratedFunctionName, lambdaGeneratedFunctionType.descriptor,
        null, emptyArray(),
    )
    val lambdaGeneratedFunctionInsn = lambdaGeneratedFunction.instructions
    val newGetterContext = getterContext.copy(
        injection = lambdaInjection, getterNode = lambdaGeneratedFunction,
    )
    DepsWriter.writeGetterInstant(lambdaGeneratedFunctionInsn, newGetterContext)
    classNode.methods.add(lambdaGeneratedFunction)

    // write invoke dynamic calls
    invokeDynamic(
        inWhichClass = inWhichComponentName,
        invokeDynamicName = "invoke",
        invokeDynamicDesc = "($inWhichComponentDesc)$function0Desc",
        lambdaGeneratedMethodName = lambdaGeneratedFunctionName,
        lambdaGeneratedMethodDesc = lambdaGeneratedFunction.desc,
        delegateMethodDesc = "()${requiredType.descriptor}",
        erasedDelegateMethodDesc = FACTORY_LAMBDA_ERASED_DESC,
    )
}