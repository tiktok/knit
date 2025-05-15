package androidx.fragment.app

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
open class Fragment : HasDefaultViewModelProviderFactory {
    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {}
    }
}
