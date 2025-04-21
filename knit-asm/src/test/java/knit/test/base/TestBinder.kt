package knit.test.base

import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ComponentMapping
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.attach2BoundMapping
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.InjectionBinder
import tiktok.knit.plugin.writer.ComponentWriter
import tiktok.knit.plugin.writer.GlobalProvidesWriter

/**
 * Created by yuejunyu on 2023/6/16
 * @author yuejunyu.0
 */
class TestContext(
    val nodes: List<ClassNode>,
    allComponents: List<ComponentClass>,
) : KnitContext {
    override val componentMap: MutableMap<InternalName, ComponentClass> =
        allComponents.associateBy { it.internalName }.toMutableMap()

    override val boundComponentMap: MutableMap<InternalName, BoundComponentClass> = mutableMapOf()

    override val globalInjectionContainer: GlobalInjectionContainer = GlobalInjectionContainer(
        componentMap.values.toList().asTestBound(),
    )

    override val inheritJudgement: InheritJudgement = BuiltinInheritJudgement

    private fun buildBindingForAll(
        context: KnitContext,
        inheritJudgement: InheritJudgement,
        componentMapping: ComponentMapping,
    ) {
        val componentMap = context.componentMap
        val boundComponentMap = context.boundComponentMap
        for (component in componentMap.values) {
            // detectDuplication(component)
            val bound = component.attach2BoundMapping(
                componentMapping, boundComponentMap,
            )
            InjectionBinder.checkComponent(inheritJudgement, bound)
            val injections = InjectionBinder.buildInjectionsForComponent(
                bound, context.globalInjectionContainer, inheritJudgement,
            )
            bound.injections = injections
            boundComponentMap[component.internalName] = bound
        }
    }

    init {
        // knit main
        buildBindingForAll(
            this, BuiltinInheritJudgement, BuiltinComponentMapping(componentMap),
        )
    }

    class BuiltinComponentMapping(
        private val componentMap: Map<InternalName, ComponentClass>,
    ) : ComponentMapping {
        override fun invoke(internalName: InternalName): ComponentClass {
            val existed = componentMap[internalName]
            if (existed != null) return existed
            val clazz = Class.forName(internalName.fqn)
            val parentNames: MutableList<String> = clazz.interfaces.map { it.name }.toMutableList()
            val superName = clazz.superclass?.name
            if (superName != null) parentNames += superName
            val parents = parentNames.map { CompositeComponent(KnitType.from(it.replace(".", "/"))) }
            return ComponentClass(internalName, parents)
        }
    }

    fun toClassLoader(): DelegateClassLoader {
        // FastNewInstance
        val componentWriter = ComponentWriter(this)
        val globalWriter = GlobalProvidesWriter(this)
        for (node in nodes) {
            if (node.name == globalProvidesInternalName) {
                globalWriter.write(node)
            } else {
                componentWriter.write(node)
            }
        }
        return childLoader(nodes)
    }
}

fun List<MetadataContainer>.toContext(): TestContext {
    val nodes = map { it.node }
    val components = map { it.toComponent() }
    return TestContext(nodes, components)
}


