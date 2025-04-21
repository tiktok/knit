package knit.test.dump

import com.google.gson.Gson
import knit.Component
import knit.Provides
import knit.di
import knit.test.base.KnitTestCase
import knit.test.base.readContainers
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.dump.ComponentDump
import tiktok.knit.plugin.dump.KnitDumper
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * Created at 2024/4/17
 * @author yuejunyu.0
 */
class DumperTest : KnitTestCase {
    @Component
    class Component1 {
        @Provides
        val valStringProvider: String = "1"

        @Component
        val composite: Composite1 = Composite1()

        val str: String by di
        val sb: StringBuilder by di
    }

    @Component
    class Composite1 {
        @Provides
        val sb: StringBuilder = StringBuilder("2")
    }

    @Test
    fun `dump class`() {
        val knitContext = readContainers(Component1::class, Composite1::class).toContext()
        val folder = createTempDirectory().toFile()
        val dumpFile = File(folder, "dump.json")
        println("dump test file to --->>>")
        println(dumpFile.path)
        if (!folder.exists()) folder.mkdirs()
        if (dumpFile.exists()) dumpFile.delete()
        KnitDumper.dumpContext(knitContext, dumpFile)
        val origin = knitContext.boundComponentMap.values.map { ComponentDump.dump(it) }
        val classDump = dumpFile.bufferedReader().use {
            @OptIn(ExperimentalStdlibApi::class)
            Gson().fromJson<List<ComponentDump>>(it, typeOf<List<ComponentDump>>().javaType)
        }
        Assertions.assertEquals(origin, classDump)
    }
}