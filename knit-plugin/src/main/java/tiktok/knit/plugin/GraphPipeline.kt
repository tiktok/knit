// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.objectweb.asm.tree.ClassNode

/**
 * Created by yuejunyu on 2025/4/15
 * @author yuejunyu.0
 */
class GraphPipeline {
    class ClassEntity(
        val className: InternalName,
        var superClasses: List<ClassEntity>,
    )

    class Graph(
        val entityMap: Map<InternalName, ClassEntity>,
        val inheritJudgement: InheritJudgement,
    )

    private val impl = GraphImpl()
    fun traverse(classNode: ClassNode) {
        impl.putClass(classNode)
    }

    fun graph(): Graph = Graph(impl.entityMap, impl)

    fun traverseFinished() {
        impl.build()
    }
}

private class GraphImpl : InheritJudgement {

    private val classMap: MutableMap<String, List<String>> = mutableMapOf()

    val entityMap: MutableMap<String, GraphPipeline.ClassEntity> = mutableMapOf()

    fun putClass(classNode: ClassNode) {
        val superName = classNode.superName
        val interfaces = classNode.interfaces
        val parents = arrayListOf(superName)
        if (!interfaces.isNullOrEmpty()) {
            parents += interfaces
        }
        classMap[classNode.name] = parents
    }

    fun build() {
        classMap.keys.forEach {
            entityMap[it] = GraphPipeline.ClassEntity(it, emptyList())
        }
        for ((className, parents) in classMap) {
            val entity = entityMap[className] ?: continue
            entity.superClasses = parents.mapNotNull { entityMap[it] }
        }
    }

    override fun inherit(thisName: InternalName, parentName: InternalName): Boolean {
        if (thisName == parentName) return true
        val entity = entityMap[thisName] ?: return false
        return entity.superClasses.any { inherit(it.className, parentName) }
    }
}
