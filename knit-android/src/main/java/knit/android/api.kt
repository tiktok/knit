package knit.android

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import knit.android.internal.KnitVMLazy

/**
 * Created by yuejunyu on 2023/7/20
 * @author yuejunyu.0
 */

typealias KnitVMFactoryOwner = HasDefaultViewModelProviderFactory

@MainThread
inline fun <reified VM : ViewModel> FragmentActivity.knitViewModel(
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): KnitVMLazy<VM> {
    val factoryPromise = factoryProducer ?: { defaultViewModelProviderFactory }
    return KnitVMLazy(VM::class, { viewModelStore }, factoryPromise)
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.knitViewModel(
    crossinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): KnitVMLazy<VM> {
    val factoryPromise = factoryProducer ?: { defaultViewModelProviderFactory }
    return KnitVMLazy(VM::class, { ownerProducer().viewModelStore }, factoryPromise)
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.knitActivityViewModel(
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): KnitVMLazy<VM> {
    val factoryPromise = factoryProducer ?: { defaultViewModelProviderFactory }
    return KnitVMLazy(VM::class, { requireActivity().viewModelStore }, factoryPromise)
}
