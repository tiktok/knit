package knit.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ReferenceSystemTest {
    private lateinit var refManager: MemoryReferenceManager
    private lateinit var objectStore: MemoryObjectStore
    private lateinit var historyTracker: ReferenceHistoryTracker
    private lateinit var eventBus: EventBus

    @BeforeEach
    fun setup() {
        refManager = MemoryReferenceManager()
        objectStore = MemoryObjectStore()
        eventBus = EventBus()
        historyTracker = ReferenceHistoryTracker(refManager, eventBus)
    }

    @Nested
    inner class BranchManagementTests {

        @Test
        fun `test create branch`() {
            val commitSha = "a".repeat(40)
            val result = refManager.createBranch("main", commitSha)

            assertTrue(result)
            assertNotNull(refManager.getBranch("main"))
            assertEquals(commitSha, refManager.getBranch("main")?.sha)
        }

        @Test
        fun `test create branch with invalid name`() {
            val commitSha = "a".repeat(40)
            val result = refManager.createBranch("invalid/name", commitSha)

            assertFalse(result)
            assertNull(refManager.getBranch("invalid/name"))
        }

        @Test
        fun `test create branch with invalid commit sha`() {
            val result = refManager.createBranch("main", "invalid-sha")

            assertFalse(result)
            assertNull(refManager.getBranch("main"))
        }

        @Test
        fun `test list branches`() {
            val commitSha = "a".repeat(40)
            refManager.createBranch("main", commitSha)
            refManager.createBranch("develop", commitSha)

            val branches = refManager.listBranches()

            assertEquals(2, branches.size)
            assertTrue(branches.any { it.getShortName() == "main" })
            assertTrue(branches.any { it.getShortName() == "develop" })
        }

        @Test
        fun `test delete branch`() {
            val commitSha = "a".repeat(40)
            refManager.createBranch("main", commitSha)

            val result = refManager.deleteBranch("main")

            assertTrue(result)
            assertNull(refManager.getBranch("main"))
        }

        @Test
        fun `test delete nonexistent branch`() {
            val result = refManager.deleteBranch("nonexistent")

            assertFalse(result)
        }
    }

    @Nested
    inner class TagManagementTests {

        @Test
        fun `test create tag`() {
            val commitSha = "a".repeat(40)
            val result = refManager.createTag("v1.0", commitSha)

            assertTrue(result)
            assertNotNull(refManager.getTag("v1.0"))
            assertEquals(commitSha, refManager.getTag("v1.0")?.sha)
        }

        @Test
        fun `test create tag with invalid name`() {
            val commitSha = "a".repeat(40)
            val result = refManager.createTag("invalid/tag", commitSha)

            assertFalse(result)
            assertNull(refManager.getTag("invalid/tag"))
        }

        @Test
        fun `test list tags`() {
            val commitSha = "a".repeat(40)
            refManager.createTag("v1.0", commitSha)
            refManager.createTag("v2.0", commitSha)

            val tags = refManager.listTags()

            assertEquals(2, tags.size)
            assertTrue(tags.any { it.getShortName() == "v1.0" })
            assertTrue(tags.any { it.getShortName() == "v2.0" })
        }

        @Test
        fun `test delete tag`() {
            val commitSha = "a".repeat(40)
            refManager.createTag("v1.0", commitSha)

            val result = refManager.deleteTag("v1.0")

            assertTrue(result)
            assertNull(refManager.getTag("v1.0"))
        }
    }

    @Nested
    inner class HeadManagementTests {

        @Test
        fun `test update head`() {
            val commitSha = "a".repeat(40)
            val result = refManager.updateHead(commitSha)

            assertTrue(result)
            assertEquals(commitSha, refManager.getHead().sha)
            assertEquals(commitSha, refManager.getCurrentCommitSha())
        }

        @Test
        fun `test update head with invalid sha`() {
            val result = refManager.updateHead("invalid-sha")

            assertFalse(result)
            assertEquals("", refManager.getHead().sha)
        }

        @Test
        fun `test get head`() {
            val commitSha = "a".repeat(40)
            refManager.updateHead(commitSha)

            val head = refManager.getHead()

            assertEquals("HEAD", head.name)
            assertEquals(commitSha, head.sha)
        }
    }

    @Nested
    inner class ReferenceResolutionTests {

        @Test
        fun `test resolve ref by name`() {
            val commitSha = "a".repeat(40)
            refManager.createBranch("main", commitSha)

            val resolved = refManager.resolveRef("main")

            assertEquals(commitSha, resolved)
        }

        @Test
        fun `test resolve ref by full name`() {
            val commitSha = "a".repeat(40)
            refManager.createBranch("main", commitSha)

            val resolved = refManager.resolveRef("refs/heads/main")

            assertEquals(commitSha, resolved)
        }

        @Test
        fun `test resolve head`() {
            val commitSha = "a".repeat(40)
            refManager.updateHead(commitSha)

            val resolved = refManager.resolveRef("HEAD")

            assertEquals(commitSha, resolved)
        }

        @Test
        fun `test resolve nonexistent ref`() {
            val resolved = refManager.resolveRef("nonexistent")

            assertNull(resolved)
        }

        @Test
        fun `test get all refs`() {
            val commitSha = "a".repeat(40)
            refManager.createBranch("main", commitSha)
            refManager.createTag("v1.0", commitSha)
            refManager.updateHead(commitSha)

            val allRefs = refManager.getAllRefs()

            assertEquals(3, allRefs.size)
            assertTrue(allRefs.any { it.name == "refs/heads/main" })
            assertTrue(allRefs.any { it.name == "refs/tags/v1.0" })
            assertTrue(allRefs.any { it.name == "HEAD" })
        }
    }

    @Nested
    inner class ReferenceHistoryTests {

        @Test
        fun `test record reference change`() {
            val oldSha = "a".repeat(40)
            val newSha = "b".repeat(40)

            historyTracker.recordChange("refs/heads/main", oldSha, newSha, "update")

            val history = historyTracker.getRefHistory("refs/heads/main")

            assertEquals(1, history.size)
            assertEquals("refs/heads/main", history[0].refName)
            assertEquals(oldSha, history[0].oldSha)
            assertEquals(newSha, history[0].newSha)
            assertEquals("update", history[0].operation)
        }

        @Test
        fun `test get all history`() {
            val sha1 = "a".repeat(40)
            val sha2 = "b".repeat(40)

            historyTracker.recordChange("refs/heads/main", sha1, sha2, "update")
            historyTracker.recordChange("refs/tags/v1.0", "", sha1, "create")

            val allHistory = historyTracker.getAllHistory()

            assertEquals(2, allHistory.size)
            assertTrue(allHistory.containsKey("refs/heads/main"))
            assertTrue(allHistory.containsKey("refs/tags/v1.0"))
        }

        @Test
        fun `test get recent changes`() {
            val sha1 = "a".repeat(40)
            val sha2 = "b".repeat(40)
            val sha3 = "c".repeat(40)

            historyTracker.recordChange("refs/heads/main", sha1, sha2, "update")
            historyTracker.recordChange("refs/heads/develop", "", sha3, "create")

            val recentChanges = historyTracker.getRecentChanges(2)

            assertEquals(2, recentChanges.size)
        }
    }
}