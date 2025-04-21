package androidx.lifecycle

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
open class ViewModelProvider {
    interface Factory {
        fun <T : ViewModel> create(modelClass: Class<T>): T {
            return modelClass.getConstructor().newInstance()
        }
    }
}