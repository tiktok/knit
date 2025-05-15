// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.element

import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.internalName
import tiktok.knit.plugin.knitInternalError
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeParameter
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.KmVariance
import kotlin.metadata.jvm.annotations

/**
 * Represents a single generic parameter in a type, e.g., `in XXX`, `out XXX`.
 *
 * @property type For example, in `out T`, `T` is the corresponding type. Of course, it may not necessarily be `T`, and could also be a specific type.
 *  The `type` is `null` only when the generic parameter is `*`.
 * @property bounds For example, in `out T : CharSequence`, `CharSequence` is the `bounds`.
 */
data class KnitGenericType(
    val variance: Int, // -1, 0, 1
    val type: KnitType?,
    val bounds: List<KnitType>,
) {
    constructor(
        variance: Int = NO_VARIANCE, type: KnitType? = null,
    ) : this(variance, type, emptyList())

    /**
     * @param neededGenericType The required type, used to check if it matches the type that the current projection can provide
     */
    fun availableFor(
        neededGenericType: KnitGenericType, inheritJudgement: InheritJudgement,
    ): Boolean {
        val (neededVariance, neededType) = neededGenericType
        if (neededType == null) return true // need <*>, so we can always allow
        if (type == null) return false // could provides <*>, but not need any, so always disallow
        for (bound in bounds) {
            if (!neededType.inherit(bound, inheritJudgement)) return false
        }
        when (neededVariance) {
            IN -> return variance != OUT && neededType.inherit(type, inheritJudgement)
            OUT -> return variance != IN && type.inherit(neededType, inheritJudgement)
            NO_VARIANCE -> return type.availableFor(neededType, inheritJudgement)
        }
        return false
    }

    override fun toString(): String {
        if (type == null) return "*"
        val varianceName = when (variance) {
            IN -> "in"
            OUT -> "out"
            NO_VARIANCE -> ""
            else -> knitInternalError("Knit generic type must be one of those variance: in/out or no variance.")
        }
        return buildString {
            append(varianceName)
            if (varianceName.isNotEmpty()) append(' ')
            append(type)
            if (bounds.isNotEmpty()) {
                append(" : ")
                for (bound in bounds) {
                    append(bound)
                }
            }
        }
    }

    companion object {
        const val OUT = -1
        const val NO_VARIANCE = 0
        const val IN = 1
        fun fromTypeProjection(
            kmTypeProjection: KmTypeProjection,
            idMapper: TypeParamIdMapper,
        ): KnitGenericType {
            val originVariance = kmTypeProjection.variance
            val originType = kmTypeProjection.type
            if (originType == null || originVariance == null) {
                return KnitGenericType(NO_VARIANCE, null, emptyList())
            }
            val classifier = originType.classifier as? KmClassifier.TypeParameter
            // get typeParameter from id
            var bounds: List<KnitType>? = null
            if (classifier != null) {
                val typeParameter = idMapper(classifier.id)
                validateTypeParameter(typeParameter)
                val upperBounds = typeParameter.upperBounds
                bounds = upperBounds.map { KnitType.fromKmType(it, idMapper = idMapper) }
            }
            // no need named for generic, it only can infer through annotation
            val type = KnitType.fromKmType(originType, idMapper = idMapper).forceWrapped()
            return fromKm(type, originVariance, bounds ?: emptyList())
        }

        fun fromTypeParam(
            kmTypeParameter: KmTypeParameter,
            idMapper: TypeParamIdMapper,
        ): KnitGenericType {
            validateTypeParameter(kmTypeParameter)
            val classifier = KnitClassifier.from(kmTypeParameter.id)
            val named = KnitType.getNamedFromAnnotations(kmTypeParameter.annotations).orEmpty()
            val type = KnitType.from(classifier, false, named).forceWrapped()
            val bounds = kmTypeParameter.upperBounds.map {
                KnitType.fromKmType(it, idMapper = idMapper)
            }
            return fromKm(type, kmTypeParameter.variance, bounds)
        }

        private fun fromKm(
            type: KnitType, variance: KmVariance, bounds: List<KnitType>
        ): KnitGenericType {
            val internalVariance = when (variance) {
                KmVariance.INVARIANT -> NO_VARIANCE
                KmVariance.IN -> IN
                KmVariance.OUT -> OUT
            }
            return KnitGenericType(internalVariance, type, bounds)
        }

        private fun validateTypeParameter(kmTypeParameter: KmTypeParameter) {
            val upperBounds = kmTypeParameter.upperBounds
            val validate = upperBounds.all { it.validateTypeForParameter(kmTypeParameter.id) }
            require(validate) {
                val boundsString = kmTypeParameter.upperBounds
                    .joinToString(prefix = "(", postfix = ")") { it.asString() }
                "type parameter <${kmTypeParameter.name}>(id:${kmTypeParameter.id}) contains " +
                    "recursive bounds: $boundsString"
            }
        }

        private fun KmType.validateTypeForParameter(id: Int): Boolean {
            val idOrNull = (classifier as? KmClassifier.TypeParameter)?.id
            if (id == idOrNull) return false
            return arguments.all { it.type?.validateTypeForParameter(id) == true }
        }

        private fun KmType.asString(): String {
            val classifier = classifier
            val internalName = classifier.internalName()
                ?: (classifier as? KmClassifier.TypeParameter)?.id?.toString()
            val args = if (arguments.isEmpty()) ""
            else arguments.joinToString(prefix = "<", postfix = ">") {
                it.type?.asString() ?: "*"
            }
            return internalName + args
        }
    }
}