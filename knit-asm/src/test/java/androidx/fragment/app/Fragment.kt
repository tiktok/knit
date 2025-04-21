package androidx.fragment.app

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import tiktok.knit.plugin.knitInternalError

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
open class Fragment: HasDefaultViewModelProviderFactory{
    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        knitInternalError("No impl")
    }
}