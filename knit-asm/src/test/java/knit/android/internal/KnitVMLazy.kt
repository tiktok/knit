package knit.android.internal

import androidx.lifecycle.ViewModel

class KnitVMLazy<VM : ViewModel> @PublishedApi internal constructor(lambda: () -> VM) :
    Lazy<VM> by lazy(lambda)