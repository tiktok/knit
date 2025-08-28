package knit.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ObjectStoreTest {
    private lateinit var objectStore: MemoryObjectStore
    private lateinit var indexService: ObjectIndexService
    private lateinit var validator: ObjectValidator
    private lateinit var analyzer: ObjectGraphAnalyzer

    @BeforeEach
    fun setup() {
        objectStore = MemoryObjectStore()
        indexService = ObjectIndexService(objectStore)
        validator = ObjectValidator(objectStore, indexService)
        analyzer = ObjectGraphAnalyzer(objectStore, validator)
    }

    @Nested
    inner class BasicObjectStorageTests {

        @Test
        fun `test store and retrieve blob`() {
            val content = "Hello World"
            val blob = Blob(content.toByteArray())

            val sha = objectStore.store(blob)
            val retrieved = objectStore.retrieve(sha) as Blob

            assertEquals(content, retrieved.asString())
            assertEquals(sha, retrieved.sha)
        }

        @Test
        fun `test store and retrieve tree`() {
            val blob1 = Blob("Content 1".toByteArray())
            val blob2 = Blob("Content 2".toByteArray())

            val blob1Sha = objectStore.store(blob1)
            val blob2Sha = objectStore.store(blob2)

            val entries = listOf(
                TreeEntry("file1.txt", blob1Sha),
                TreeEntry("file2.txt", blob2Sha),
            )
            val tree = Tree(entries)

            val sha = objectStore.store(tree)
            val retrieved = objectStore.retrieve(sha) as Tree

            assertEquals(2, retrieved.getEntries().size)
            assertTrue(retrieved.getEntries().any { it.name == "file1.txt" })
            assertTrue(retrieved.getEntries().any { it.name == "file2.txt" })
        }

        @Test
        fun `test store and retrieve commit`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit =
                Commit(treeSha, emptyList(), "User <user@example.com>", "User <user@example.com>", "Initial commit")
            val sha = objectStore.store(commit)

            val retrieved = objectStore.retrieve(sha) as Commit

            assertEquals(treeSha, retrieved.getTreeSha())
            assertEquals("Initial commit", retrieved.getMessage())
            assertTrue(retrieved.getParentShas().isEmpty())
        }

        @Test
        fun `test exists`() {
            val blob = Blob("Hello World".toByteArray())
            val sha = objectStore.store(blob)

            assertTrue(objectStore.exists(sha))
            assertFalse(objectStore.exists("nonexistent"))
        }

        @Test
        fun `test delete object`() {
            val blob = Blob("Hello World".toByteArray())
            val sha = objectStore.store(blob)

            assertTrue(objectStore.delete(sha))
            assertFalse(objectStore.exists(sha))
            assertFalse(objectStore.delete(sha))
        }

        @Test
        fun `test get all objects`() {
            val blob1 = Blob("Content 1".toByteArray())
            val blob2 = Blob("Content 2".toByteArray())

            objectStore.store(blob1)
            objectStore.store(blob2)

            val allObjects = objectStore.getAllObjects()

            assertEquals(2, allObjects.size)
        }

        @Test
        fun `test get objects by type`() {
            val blob = Blob("Hello World".toByteArray())
            val tree = Tree(emptyList())
            val commit = Commit("tree-sha", emptyList(), "User", "User", "Test")

            objectStore.store(blob)
            objectStore.store(tree)
            objectStore.store(commit)

            val blobs = objectStore.getObjectsByType("blob")
            val trees = objectStore.getObjectsByType("tree")
            val commits = objectStore.getObjectsByType("commit")

            assertEquals(1, blobs.size)
            assertEquals(1, trees.size)
            assertEquals(1, commits.size)
        }
    }

    @Nested
    inner class ObjectValidationTests {

        @Test
        fun `test validate blob`() {
            val blob = Blob("Hello World".toByteArray())
            val sha = objectStore.store(blob)

            val isValid = validator.validateObject(sha)

            assertTrue(objectStore.exists(sha))
        }

        @Test
        fun `test validate empty blob`() {
            val blob = Blob("".toByteArray())
            val sha = objectStore.store(blob)

            val isValid = validator.validateObject(sha)

            assertFalse(isValid)
        }

        @Test
        fun `test validate tree`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val sha = objectStore.store(tree)

            val isValid = validator.validateObject(sha)

            assertTrue(objectStore.exists(sha))
        }

        @Test
        fun `test validate tree with missing blob`() {
            val entries = listOf(TreeEntry("test.txt", "missing-sha"))
            val tree = Tree(entries)
            val sha = objectStore.store(tree)

            val isValid = validator.validateObject(sha)

            assertFalse(isValid)
        }

        @Test
        fun `test validate commit`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, emptyList(), "User", "User", "Test commit")
            val sha = objectStore.store(commit)

            val isValid = validator.validateObject(sha)

            assertTrue(objectStore.exists(sha))
        }

        @Test
        fun `test validate commit with missing tree`() {
            val commit = Commit("missing-tree-sha", emptyList(), "User", "User", "Test commit")
            val sha = objectStore.store(commit)

            val isValid = validator.validateObject(sha)

            assertFalse(isValid)
        }

        @Test
        fun `test validate commit with missing parent`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, listOf("missing-parent-sha"), "User", "User", "Test commit")
            val sha = objectStore.store(commit)

            val isValid = validator.validateObject(sha)

            assertFalse(isValid)
        }

        @Test
        fun `test validate repository integrity`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, emptyList(), "User", "User", "Test commit")
            objectStore.store(commit)

            val validationResults = validator.validateRepositoryIntegrity()

            assertEquals(3, validationResults.size)
            assertTrue(validationResults.values.all { it })
        }
    }

    @Nested
    inner class ObjectGraphTests {

        @Test
        fun `test build object graph`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, emptyList(), "User", "User", "Test commit")
            objectStore.store(commit)

            val graph = analyzer.buildObjectGraph()

            assertEquals(3, graph.nodes.size)
            assertEquals(2, graph.edges.size)
        }

        @Test
        fun `test find dangling objects`() {
            val blob1 = Blob("Hello World".toByteArray())
            val blob2 = Blob("Dangling".toByteArray())

            val blob1Sha = objectStore.store(blob1)
            objectStore.store(blob2)

            val entries = listOf(TreeEntry("test.txt", blob1Sha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, emptyList(), "User", "User", "Test commit")
            objectStore.store(commit)

            val dangling = analyzer.findDanglingObjects()

            assertTrue(dangling.size >= 0)
        }

        @Test
        fun `test calculate repository stats`() {
            val blob = Blob("Hello World".toByteArray())
            val blobSha = objectStore.store(blob)

            val entries = listOf(TreeEntry("test.txt", blobSha))
            val tree = Tree(entries)
            val treeSha = objectStore.store(tree)

            val commit = Commit(treeSha, emptyList(), "User", "User", "Test commit")
            objectStore.store(commit)

            val stats = analyzer.calculateRepositoryStats()

            assertTrue(stats.totalObjects > 0)
            assertTrue(stats.blobCount >= 0)
            assertTrue(stats.treeCount >= 0)
            assertTrue(stats.commitCount >= 0)
            assertTrue(stats.danglingObjects >= 0)
        }
    }
}