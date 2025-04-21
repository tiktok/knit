package tiktok.knit.sample

import knit.Component
import knit.Provides
import knit.Singleton

/**
 * Created by yuejunyu on 2023/7/6
 * @author yuejunyu.0
 */
@Provides(IAnother::class)
@Singleton
class Another : IAnother {
    override fun call(): String {
        return "foo"
    }
}

interface IAnother {
    fun call(): String
}

@Component
class AA {
    @Provides(IAnother::class) val another: Another = Another()
}
