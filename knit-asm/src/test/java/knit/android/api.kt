package knit.android

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import knit.android.internal.KnitVMLazy

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
inline fun <reified VM : ViewModel> Fragment.knitViewModel(): KnitVMLazy<VM> {
    return KnitVMLazy { getDefaultViewModelProviderFactory().create(VM::class.java) }
}