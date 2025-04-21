package knit.test.base

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Created by yuejunyu on 2023/6/16
 * @author yuejunyu.0
 */

class ClassObj(
    private val clazz: Class<*>,
    val obj: Any?,
) {
    fun method(name: String): MethodObj {
        val m = clazz.methods.first { it.name == name }
        return MethodObj(this, m)
    }

    fun field(name: String): FieldObj {
        val field = clazz.fields.first { it.name == name }
        return FieldObj(this, field)
    }

    inline fun <reified T> obj(): T = obj as T

    operator fun get(name: String): MethodObj = method(name)
}

class MethodObj(
    private val classObj: ClassObj,
    private val method: Method,
) {
    operator fun invoke(vararg args: Any?, setAccessible: Boolean = true): ClassObj {
        method.isAccessible = setAccessible
        val result = method.invoke(classObj.obj, *args)
        val clazz = result?.javaClass ?: method.returnType
        return ClassObj(clazz, result)
    }
}

class FieldObj(
    private val classObj: ClassObj,
    private val field: Field,
) {
    operator fun invoke(setAccessible: Boolean = true): ClassObj {
        field.isAccessible = setAccessible
        val result = field.get(classObj.obj)
        val clazz = result?.javaClass ?: field.type
        return ClassObj(clazz, result)
    }
}

inline fun <reified T> ClassLoader.load(): Class<*> {
    return loadClass(T::class.java.name)
}

inline fun <reified T> ClassLoader.new(vararg args: Any?): ClassObj {
    return load<T>().new(*args)
}

fun Class<*>.new(vararg args: Any?): ClassObj {
    val count = args.count()
    val obj = declaredConstructors.first {
        it.parameterCount == count
    }.apply { isAccessible = true }.newInstance(*args)
    return ClassObj(this, obj)
}
