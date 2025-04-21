package knit.android.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlin.reflect.KClass

/**
 * Created by yuejunyu on 2024/7/3
 * @author yuejunyu.0
 */
class KnitVMLazy<VM : ViewModel> @PublishedApi internal constructor(
    private val viewModelClass: KClass<VM>,
    private val storeProducer: () -> ViewModelStore,
    private val factoryProducer: () -> ViewModelProvider.Factory,
) : Lazy<VM> by ViewModelLazy(viewModelClass, storeProducer, factoryProducer)
