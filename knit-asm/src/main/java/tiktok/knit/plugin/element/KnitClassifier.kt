// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import org.objectweb.asm.Type
import tiktok.knit.plugin.DescName
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.descName
import tiktok.knit.plugin.internalName
import kotlin.metadata.KmClassifier
import kotlin.metadata.internal.metadata.jvm.deserialization.ClassMapperLite
import kotlin.reflect.KClass

/** Used to describe the type name, or it can also be a generic type id */
data class KnitClassifier(
    val desc: DescName, // Ljava/lang/Object;
    val id: Int?, // it only used when this is a type argument, such as: `T`
) {
    constructor(desc: DescName) : this(desc, null)
    constructor(id: Int) : this(id.toString(), id)

    fun isTypeParameter(): Boolean = id != null

    companion object {
        fun from(
            internalName: InternalName, typeParameterIndex: Int? = null,
        ): KnitClassifier {
            val realDesc = typeParameterIndex?.toString() ?: ClassMapperLite.mapClass(internalName)
            return KnitClassifier(realDesc, typeParameterIndex)
        }

        fun from(
            typeParameterIndex: Int,
        ): KnitClassifier {
            val realDesc = typeParameterIndex.toString()
            return KnitClassifier(realDesc, typeParameterIndex)
        }

        fun from(
            classifier: KmClassifier
        ): KnitClassifier {
            val internalName = classifier.internalName()
            if (internalName != null) return from(internalName)
            return from("", (classifier as KmClassifier.TypeParameter).id)
        }

        fun from(kClass: KClass<*>): KnitClassifier {
            return KnitClassifier(kClass.descName)
        }

        fun from(asmType: Type): KnitClassifier {
            return from(asmType.internalName)
        }

        fun fromArray(
            innerClassifier: KnitClassifier
        ): KnitClassifier = KnitClassifier("[${innerClassifier.desc}")
    }
}