// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin

import org.gradle.api.provider.Property

/**
 * Created by yuejunyu on 2025/8/14
 * Extension for configuring Knit plugin settings.
 */
interface KnitExtension {
    /**
     * The output path for the dependency tree JSON file.
     * Defaults to "build/knit/dependency-tree.json"
     */
    val dependencyTreeOutputPath: Property<String>
}