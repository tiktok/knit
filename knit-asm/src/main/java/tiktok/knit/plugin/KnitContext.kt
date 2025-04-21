// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.injection.GlobalInjectionContainer

interface KnitContext {
    val componentMap: MutableMap<InternalName, ComponentClass>
    val boundComponentMap: MutableMap<InternalName, BoundComponentClass>
    val globalInjectionContainer: GlobalInjectionContainer
    val inheritJudgement: InheritJudgement
}
