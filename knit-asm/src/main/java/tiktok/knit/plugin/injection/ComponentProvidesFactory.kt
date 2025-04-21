// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection

import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.element.BoundComponentClass

/**
 * create [SourcedMethod] list for [BoundComponentClass]
 *
 * Created by yuejunyu on 2023/10/23
 *
 * alias [CPF]
 * @author yuejunyu.0
 */
object ComponentProvidesFactory {
    fun all(
        componentClass: BoundComponentClass,
        includePrivate: Boolean,
    ): List<SourcedMethod> = buildListCompat {
        with(componentClass) {
            addAll(self(includePrivate))
            addAll(parent())
            addAll(composite(includePrivate))
        }
    }

    private fun BoundComponentClass.self(includePrivate: Boolean)
        : Sequence<SourcedMethod> = sequence {
        val selfAllProvides = provides.filter { !it.staticProvides }.filter {
            it.isPublic || includePrivate // public or include private
        }
        selfAllProvides.forEach {
            yield(Injection.From.SELF(it))
        }
    }

    private fun BoundComponentClass.parent()
        : Sequence<SourcedMethod> = sequence {
        parents.forEach { parent ->
            all(parent.component, false).forEach {
                // override order to parent
                yield(Injection.From.PARENT(it.method))
            }
        }
    }

    private fun BoundComponentClass.composite(includePrivate: Boolean)
        : Sequence<SourcedMethod> = sequence {
        compositeComponents.values.forEach { component ->
            if (component.isPublic || includePrivate) {
                // include private will only include itself component, not recursive.
                all(component.component, false).forEach { providesMethod ->
                    yield(Injection.From.COMPOSITE(providesMethod.method))
                }
            }
        }
    }
}

typealias CPF = ComponentProvidesFactory
