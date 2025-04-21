package knit.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import tiktok.knit.plugin.intConst

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
class UtilKtTest {
    @Test
    fun `test intConst`() {
        val list = InsnList().apply {
            intConst(-1)
            intConst(5)
            intConst(Byte.MAX_VALUE.toInt())
            intConst(Short.MAX_VALUE.toInt())
            intConst(Int.MAX_VALUE - 100)
        }.toList()
        Assertions.assertEquals(Opcodes.ICONST_M1, list[0].opcode)
        Assertions.assertEquals(Opcodes.ICONST_5, list[1].opcode)
        Assertions.assertEquals(Opcodes.BIPUSH, list[2].opcode)
        Assertions.assertEquals(Opcodes.SIPUSH, list[3].opcode)
        Assertions.assertEquals(Opcodes.LDC, list[4].opcode)
    }
}