package knit.test.sample

import knit.Provides
import knit.di

/**
 * Created by yuejunyu on 2024/7/26
 * @author yuejunyu.0
 */
@Provides
class FileLevelAutoDetectSample {
    @JvmField
    val verifyValue = 1145
}

val fileLevelInjected: FileLevelAutoDetectSample by di
