// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.injection.checker

import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ProvidesMethod

/**
 * Created by yuejunyu on 2023/9/4
 * @author yuejunyu.0
 */
interface ProvidesChecker : ComponentChecker {
    fun checkProvides(component: BoundComponentClass, provides: ProvidesMethod, inheritJudgement: InheritJudgement)
    override fun check(inheritJudgement: InheritJudgement, component: BoundComponentClass) {
        val allProvides = component.provides
        for (provides in allProvides) {
            checkProvides(component, provides, inheritJudgement)
        }
    }
}