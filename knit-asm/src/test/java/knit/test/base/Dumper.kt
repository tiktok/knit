package knit.test.base

import org.junit.jupiter.api.Test
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.util.ASMifier
import org.objectweb.asm.util.Printer
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor

/**
 * Created by yuejunyu on 2023/7/14
 * @author yuejunyu.0
 */
object Dumper {
    fun dumpInsnList(insnList: InsnList, printer: Printer = ASMifier()): List<String> {
        val trace = TraceMethodVisitor(printer)
        insnList.accept(trace)
        return printer.text.map { it.toString() }
    }

    fun dumpInsnListText(insnList: InsnList): List<String> {
        return dumpInsnList(insnList, Textifier())
    }

    @Test
    fun `test dump insn list`() {
        val clazz = readMetadataFrom<Dumper>()
        val method = clazz.node.methods.first { it.name == "dumpInsnList" }
        val insnList = method.instructions
        val result = dumpInsnList(insnList)
        assert(result.isNotEmpty())
    }
}
