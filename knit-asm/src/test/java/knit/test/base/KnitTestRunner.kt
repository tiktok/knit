package knit.test.base

import knit.Option
import knit.android.internal.VMPFactoryImpl
import knit.internal.GlobalProvides
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.util.ReflectionUtils
import java.lang.reflect.Method


/**
 * Created by yuejunyu on 2023/9/20
 * @author yuejunyu.0
 */
class KnitTestInterceptor : InvocationInterceptor {
    override fun interceptTestTemplateMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        interceptMethod(invocation, invocationContext, extensionContext)
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        interceptMethod(invocation, invocationContext, extensionContext)
    }

    private fun interceptMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext
    ) {
        val testClass = extensionContext.testClass.get()
        val knitClassLoader = knitClassLoader(testClass)
        if (knitClassLoader == null) {
            invocation.proceed()
            return
        } else {
            invocation.skip()
        }
        Thread.currentThread().contextClassLoader = knitClassLoader

        val methodName = invocationContext.executable.name
        val clazz = knitClassLoader.loadClass(testClass.name)

        val testInstance = ReflectionUtils.newInstance(clazz)
        val method = ReflectionUtils.findMethod(clazz, methodName).get()
        ReflectionUtils.invokeMethod(
            method, testInstance,
        )
    }
}

@Target(AnnotationTarget.CLASS)
internal annotation class KnitMock

private fun knitClassLoader(clazz: Class<*>): DelegateClassLoader? {
    val allChildren = clazz.allChildren()
        .filter { it.annotations.any { anno -> anno is KnitMock } }
        .map { it.kotlin }.toSet().toTypedArray()
    if (allChildren.isEmpty()) return null
    return readContainers(
        clazz.kotlin,
        GlobalProvides::class,
        Option::class,
        VMPFactoryImpl::class,
        *allChildren,
    ).toContext().toClassLoader()
}

private fun Class<*>.allChildren(): Sequence<Class<*>> = sequence {
    yield(this@allChildren)
    classes.forEach {
        yieldAll(it.allChildren())
    }
}


