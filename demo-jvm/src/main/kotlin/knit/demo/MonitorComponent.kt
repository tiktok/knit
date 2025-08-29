package knit.demo

import knit.Component
import knit.Provides

@Component
@Provides
class MonitorComponent(
    @Provides val eventBus: EventBus,
    @Provides val auditLogger: AuditLogger,
    @Provides val performanceMonitor: PerformanceMonitor,
    @Provides val objectGraphAnalyzer: ObjectGraphAnalyzer,
)
