package knit.test

import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.readMetadataFrom
import knit.test.sample.GenericClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.element.KnitClassifier
import tiktok.knit.plugin.element.KnitGenericType
import tiktok.knit.plugin.element.KnitType

/**
 * Created by yuejunyu on 2023/6/5
 * @author yuejunyu.0
 */
@TestTargetClass(
    KnitClassifier::class, KnitType::class, KnitGenericType::class, MetadataContainer::class,
)
class MetaDataReadTest : KnitTestCase {
    @Test
    fun testReadMetadata() {
        val metadata = readMetadataFrom<MetaDataReadTest>()
        assertTrue(metadata.functions.any { it.name == "testReadMetadata" })
    }

    @Test
    fun `read generic class Metadata`() {
        val metadataContainer = readMetadataFrom<GenericClass<*>>()
        val classNode = metadataContainer.node
        val kmClass = metadataContainer.kmClassOrNull()
        requireNotNull(kmClass)
        val type = KnitType.fromClass(metadataContainer, classNode)
        val charSequenceClassifier = KnitClassifier.from(CharSequence::class)
        assertEquals("custom name!", type.named)
        assertEquals(charSequenceClassifier, type.typeParams[0].bounds[0].classifier)
        assertEquals(KnitGenericType.OUT, type.typeParams[0].variance)
    }
}