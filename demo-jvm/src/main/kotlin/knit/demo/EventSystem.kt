package knit.demo

import knit.Provides
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

// Event system
sealed class Event {
    abstract val timestamp: Instant
    abstract val eventType: String
}

// Git command events
data class GitCommandEvent(
    override val eventType: String,
    val command: String,
    val args: List<String>,
    val result: CommandResult,
    override val timestamp: Instant = Instant.now()
) : Event()

// File system events
data class FileSystemEvent(
    override val eventType: String,
    val path: String,
    val operation: String,
    override val timestamp: Instant = Instant.now()
) : Event()

// Reference events
data class ReferenceEvent(
    override val eventType: String,
    val refName: String,
    val oldSha: String,
    val newSha: String,
    override val timestamp: Instant = Instant.now()
) : Event()

// Object storage events
data class ObjectStoreEvent(
    override val eventType: String,
    val sha: String,
    val objectType: String,
    override val timestamp: Instant = Instant.now()
) : Event()

// Event bus
@Provides
class EventBus {
    private val listeners = mutableMapOf<String, MutableList<(Event) -> Unit>>()
    private val eventHistory = ConcurrentLinkedQueue<Event>()
    
    fun subscribe(eventType: String, listener: (Event) -> Unit) {
        listeners.getOrPut(eventType) { mutableListOf() }.add(listener)
    }
    
    fun publish(event: Event) {
        eventHistory.add(event)
        listeners[event.eventType]?.forEach { listener ->
            listener(event)
        }
    }
    
    fun getRecentEvents(limit: Int = 10): List<Event> {
        return eventHistory.toList().takeLast(limit)
    }
    
    fun getEventsByType(eventType: String): List<Event> {
        return eventHistory.filter { it.eventType == eventType }
    }
    
    fun getAllEvents(): List<Event> {
        return eventHistory.toList()
    }
    
    fun clear() {
        eventHistory.clear()
    }
}

// Audit logging system
@Provides
class AuditLogger(
     private val eventBus: EventBus
) {
    private val auditLog = mutableListOf<String>()
    
    init {
        eventBus.subscribe("git_command") { event ->
            if (event is GitCommandEvent) {
                logGitCommand(event)
            }
        }
        
        eventBus.subscribe("filesystem") { event ->
            if (event is FileSystemEvent) {
                logFileSystemOperation(event)
            }
        }
        
        eventBus.subscribe("reference") { event ->
            if (event is ReferenceEvent) {
                logReferenceChange(event)
            }
        }
        
        eventBus.subscribe("object_store") { event ->
            if (event is ObjectStoreEvent) {
                logObjectStoreOperation(event)
            }
        }
    }
    
    private fun logGitCommand(event: GitCommandEvent) {
        val logEntry = "[${event.timestamp}] GIT: ${event.command} ${event.args.joinToString(" ")} -> ${event.result}"
        auditLog.add(logEntry)
    }
    
    private fun logFileSystemOperation(event: FileSystemEvent) {
        val logEntry = "[${event.timestamp}] FS: ${event.operation} ${event.path}"
        auditLog.add(logEntry)
    }
    
    private fun logReferenceChange(event: ReferenceEvent) {
        val logEntry = "[${event.timestamp}] REF: ${event.refName} ${event.oldSha.take(7)} -> ${event.newSha.take(7)}"
        auditLog.add(logEntry)
    }
    
    private fun logObjectStoreOperation(event: ObjectStoreEvent) {
        val logEntry = "[${event.timestamp}] OBJ: ${event.eventType} ${event.sha.take(7)} (${event.objectType})"
        auditLog.add(logEntry)
    }
    
    fun getAuditLog(): List<String> = auditLog.toList()
    
    fun getRecentLogEntries(limit: Int = 10): List<String> {
        return auditLog.toList().takeLast(limit)
    }
    
    fun clearLog() {
        auditLog.clear()
    }
    
    fun exportLog(): String {
        return auditLog.joinToString("\n")
    }
}

// Performance monitor
@Provides
class PerformanceMonitor(
     private val eventBus: EventBus
) {
    private val operationTimes = mutableMapOf<String, MutableList<Long>>()
    
    fun <T> measureOperation(operationName: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        
        operationTimes.getOrPut(operationName) { mutableListOf() }.add(duration)
        
        eventBus.publish(ObjectStoreEvent(
            eventType = "performance",
            sha = operationName,
            objectType = "operation"
        ))
        
        return result
    }
    
    fun getAverageTime(operationName: String): Double {
        val times = operationTimes[operationName] ?: return 0.0
        return times.average()
    }
    
    fun getOperationStats(): Map<String, Double> {
        return operationTimes.mapValues { (_, times) -> times.average() }
    }
    
    fun getSlowestOperations(limit: Int = 5): List<Pair<String, Double>> {
        return operationTimes.mapValues { (_, times) -> times.average() }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    fun getTotalOperations(): Int {
        return operationTimes.values.sumOf { it.size }
    }
    
    fun getOperationCount(operationName: String): Int {
        return operationTimes[operationName]?.size ?: 0
    }
}

// Event listener manager
@Provides
class EventListenerManager(
     private val eventBus: EventBus,
     private val auditLogger: AuditLogger,
     private val performanceMonitor: PerformanceMonitor
) {
    init {
        setupEventListeners()
    }
    
    private fun setupEventListeners() {
        // Performance monitoring listener
        eventBus.subscribe("git_command") { event ->
            if (event is GitCommandEvent) {
                performanceMonitor.measureOperation("git_${event.command}") {
                    // Record performance data
                }
            }
        }
        
        // System health check listener
        eventBus.subscribe("object_store") { event ->
            if (event is ObjectStoreEvent && event.eventType == "store") {
                // Check object storage health status
            }
        }
    }
    
    fun getSystemHealthReport(): Map<String, Any> {
        return mapOf(
            "total_events" to eventBus.getAllEvents().size,
            "recent_events" to eventBus.getRecentEvents(5),
            "audit_log_size" to auditLogger.getAuditLog().size,
            "performance_stats" to performanceMonitor.getOperationStats(),
            "slowest_operations" to performanceMonitor.getSlowestOperations(3)
        )
    }
}