// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

@file:Suppress(
    "PLATFORM_CLASS_MAPPED_TO_KOTLIN",
    "RemoveRedundantQualifierName",
    "NOTHING_TO_INLINE",
    "unused",
)

package knit

/**
 * Created by yuejunyu on 2023/7/24
 * @author yuejunyu.0
 */
typealias JByte = java.lang.Byte
typealias JShort = java.lang.Short
typealias JInt = java.lang.Integer
typealias JLong = java.lang.Long
typealias JFloat = java.lang.Float
typealias JDouble = java.lang.Double
typealias JChar = java.lang.Character
typealias JBoolean = java.lang.Boolean

inline fun Byte.boxed(): JByte = this as JByte
inline fun Short.boxed(): JShort = this as JShort
inline fun Int.boxed(): JInt = this as JInt
inline fun Long.boxed(): JLong = this as JLong
inline fun Float.boxed(): JFloat = this as JFloat
inline fun Double.boxed(): JDouble = this as JDouble
inline fun Char.boxed(): JChar = this as JChar
inline fun Boolean.boxed(): JBoolean = this as JBoolean

//fun <T1> di(i1: T1): DIStubImpl = DIStubImpl
//fun <T1, T2> di(i1: T1, i2: T2): DIStubImpl = DIStubImpl
//fun <T1, T2, T3> di(i1: T1, i2: T2, i3: T3): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4> di(i1: T1, i2: T2, i3: T3, i4: T4): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5, T6> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5, i6: T6): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5, T6, T7> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5, i6: T6, i7: T7): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5, T6, T7, T8> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5, i6: T6, i7: T7, i8: T8): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5, T6, T7, T8, T9> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5, i6: T6, i7: T7, i8: T8, i9: T9): DIStubImpl = DIStubImpl
//fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> di(i1: T1, i2: T2, i3: T3, i4: T4, i5: T5, i6: T6, i7: T7, i8: T8, i9: T9, i10: T10): DIStubImpl = DIStubImpl
