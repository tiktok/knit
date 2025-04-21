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
object ProvidesParentChecker : ProvidesChecker {
    override fun checkProvides(
        component: BoundComponentClass, provides: ProvidesMethod, inheritJudgement: InheritJudgement
    ) {
        val types = provides.providesTypes
        val actualType = provides.actualType
        if (types.size == 1 && actualType == types.first()) return
        for (type in types) {
            require(inheritJudgement(actualType.classifier.desc, type.classifier.desc)) {
                "As a @Provides parent, ${actualType.classifier.desc} must extends from ${type.classifier.desc}"
            }
        }
    }
}