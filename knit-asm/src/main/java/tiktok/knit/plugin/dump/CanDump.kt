// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

package tiktok.knit.plugin.dump

/**
 * Created at 2024/4/17
 * @author yuejunyu.0
 */
interface CanDump<T, R> {
    fun dump(origin: T): R
}
