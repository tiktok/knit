package knit.android.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import knit.Factory
import knit.test.base.KnitMock

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
@KnitMock
class VMPFactoryImpl(
    arrayMap: Array<*>,
    private val parent: ViewModelProvider.Factory?,
) : ViewModelProvider.Factory {
    private val factories: Map<Class<*>, Factory<*>>

    init {
        val result = HashMap<Class<*>, Factory<*>>()
        var i = 0
        while (i < arrayMap.size) {
            val key = arrayMap[i] as Class<*>
            val factory = arrayMap[i + 1] as Factory<*>
            result[key] = factory
            i += 2
        }
        factories = result
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val factoryByDirect = factories[modelClass]
        if (factoryByDirect != null) return factoryByDirect() as T

        // factory by implementation type.
        for ((classKey, vmFactory) in factories) {
            if (classKey.isAssignableFrom(modelClass)) {
                @Suppress("UNCHECKED_CAST")
                return vmFactory() as T
            }
        }

        val defaultProvider = parent ?: throw IllegalArgumentException("no parent for ${modelClass.name}.")
        return defaultProvider.create(modelClass)
    }

    companion object {
        // only called from bytecode
        @JvmStatic
        fun from(arrayMap: Array<*>, parent: ViewModelProvider.Factory?): ViewModelProvider.Factory {
            return VMPFactoryImpl(arrayMap, parent)
        }
    }
}
