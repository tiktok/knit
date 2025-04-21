package androidx.lifecycle

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
interface HasDefaultViewModelProviderFactory {
    fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory
}