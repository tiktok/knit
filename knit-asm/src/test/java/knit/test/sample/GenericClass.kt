package knit.test.sample

import knit.Named

/**
 * Created by yuejunyu on 2023/6/7
 * @author yuejunyu.0
 */
@Named("custom name!")
class GenericClass<out T : CharSequence> {
    @Suppress("REDUNDANT_PROJECTION")
    fun <R : Any> getFoo(): Map<out T, out R> {
        return emptyMap()
    }
}