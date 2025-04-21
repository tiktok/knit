package knit.test

import knit.test.base.BuiltinInheritJudgement
import knit.test.base.TestTargetClass
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.descName
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
@TestTargetClass(InheritJudgement::class)
class InheritJudgementTest {
    @Test
    fun `test inherit`() {
        val stringType = String::class.descName
        val charSequenceType = CharSequence::class.descName
        Assertions.assertTrue(BuiltinInheritJudgement(stringType, charSequenceType))
        Assertions.assertFalse(BuiltinInheritJudgement(charSequenceType, stringType))
    }

    @Test
    fun `test always false`() {
        val stringType = String::class.descName
        val charSequenceType = CharSequence::class.descName
        Assertions.assertFalse(InheritJudgement.AlwaysFalse(stringType, charSequenceType))
        Assertions.assertFalse(
            InheritJudgement.AlwaysFalse.inherit(
                String::class.internalName, CharSequence::class.internalName,
            ),
        )
        Assertions.assertFalse(BuiltinInheritJudgement(charSequenceType, stringType))
    }
}