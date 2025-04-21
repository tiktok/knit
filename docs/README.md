# Knit Docs

## Advance usages

Before starting to know advance usages, we must know **what is component**.

In Knit, component is an injection container for provide or consume something.

Technically, Knit needs to know which class is Knit component, at the backend, Knit has some ways to do that.

- If some properties inside class marked with `by di`, this class will be a Knit component.
- If any `@Provides` or `@Component` used in class, this class will be a Knit component.
- Classes marked with `@Component` will be treated as a component.

**Knit will do nothing if the class isn't a Knit component.** 

### Singleton Support

By default, if the producer does not declare `Singleton`, different consumers **will generate different objects**. If you want the object to be created only once, you need to declare `Singleton` like following:

- In this code, it is specified that the injected `DataCenter` object is globally unique

    ```kotlin
    @Singleton
    @Provides
    class DataCenter(val key: String)

    class MainComponent {
        val dataCenter: DataCenter by di
    }

    class OtherComponent {
        val dataCenter: DataCenter by di // same object with MainComponent's dataCenter
    }
    ```

Of course, in a regular component, it is also possible to specify a certain property or method, which is unique within this component, such as in the following example:

- `provideString` will executes its internal logic only on the first access

    ```kotlin
    class MainComponent {
        @Singleton // provide as singleton
        @Provides
        fun provideString(): String = // ...
    }
    ```

#### Notice

- `Singleton` does not recommend **nullable types** (or undefined behavior)

This is because we are behind the bytecode to determine whether its creation is performed by the `IFNULL` instruction.
We do not want to introduce additional complexity to support nullability, which may make the bytecode more complex, but the necessity is not strong, but we are welcome if you have some
good ideas to improve it.

### Component combination

#### Composite component

This is achieved by adding properties annotated by `@Component`. The component after adding the annotation will be combined with the current component, so that the current component has all capabilities that other components can provide, as shown in the following example:

```kotlin
class MainActivityComponent(
    @Provides val app: Application,
    @Provides val device: Device,
    @Provides private val network: Network,
)
```

By adding `@Component`, other Components can also access all the capabilities defined in `MainActivityComponent` (like Application, Device, and Network), but can not obtain the properties that are not accessible to consumers (private).

```kotlin
class HomepageComponent(
    @Component val main: MainActivityComponent
) {
    val app: Application by di // inject Application from main
    val device: Device by di // inject Device from main
    val network: Network by di // ❌ cannot inject from main，because Network is private
}
```

The code behind it will be generated in bytecode. When obtaining the app and device, **HomepageComponent** will search for:

1. Whether the current component is provided, and if so, use it for inject

2. Check if all added `@Component` properties can be provided, if found, then returned, and if several Components
   provide the same type, it will be a compile error. In this case, you need to manually implement the properties to
   clear which component to get from.

For more principle about how Knit provide a dependency, check [Principles of Knit Dependency Lookup](Principle_of_Dependency_Lookup.md)

#### Component inheritance

In many cases, we hope that there are some commonalities between some components , and we hope to abstract them out of the interface or parent class, so we can declare `@Provides` in the parent class without the need to declare them in the subclass, but in this case, `@Component` annotation is needed to be added for mark it as a knit component. Here is an example:

```kotlin
interface FooApi {
    @Provides
    fun provideBar(): Bar
}

@Component // in business module
class FooImpl : FooApi {
    override fun provideBar(): Bar = Bar()
}

@Component // in test module
class FooTestImpl : FooApi {
    override fun provideBar(): Bar = TestBar()
}
```

### Factory / Loadable

For `Factory`, it can provide consumers within a lambda in these 2 ways:

- Using the `Factory` defined by the framework, the framework will automatically inject into it
- Or use the function type such as `() -> DataCenter`

> In fact, the implementation of Factory is `typealias Factory <T> = () -> T`

```kotlin
class MainPageComponent(
    @Provides val key: String
) {
    val dataCenter: Factory<DataCenter> by di
    val dataCenter: () -> DataCenter by di // they are same
}
```

In some cases, you also want to control the loading and unloading of dependencies. At this time, **Loadable** can be used, and the framework will automatically inject and implement Loadable capabilities:

```kotlin
class Loadable<T> {
    fun load(): T // force load and return
    fun get(): T? // return current value, return null if it has been unloaded or not loaded.
    fun unload(): T? // unload, and return the value before unload
}

class MyComponent {
    val showToastAbility: Loadable<ShowToastAbility> by di
}
```

### Named

In some cases, we will need to inject the type provided by the specified provider, and we can use the Named function at this time:

Producers can tag types with the Named annotation

```kotlin
class MainActivityComponent {
    @Provides
    val value: @Named("value") String = "foo"
}

@Named("myData") // it can also mark at class, it means provides DataCenter with specified name
class DataCenter
```

Consumers can also flag constructor parameters or val by di :

```kotlin
@Provides
class DataCenter(
    val ctorValue: @Named("value") String // constructor injection
) {
    val value: @Named("value") String by di  // named injection
}
```

If you think that writing string is easy to mismatch and not very good for jumping in the IDE , there is another way, which is more type safe and easy to jump inference

> It will use its representation in bytecode as the name, such as: java/lang/Object

```kotlin
@Provides
val value: @Named(qualifier = SomeClass::class) String = "foo" // producer
val value: @Named(qualifier = SomeClass::class) String by di  // consumer
```

### Interface Injection

The interface injection method requires an explicit declaration of the type to be injected, but in the case of the separation of the api and impl modules, we often can not get the real object reference, and can only use the interface reference exposed at the api layer, so we need to have automatic ability to inject **interfaces** or **abstract classes**. In this case, the interface to be provided needs to be explicitly declared, as follows

```kotlin
@Provides(IDataCenter::class)
class DataCenter(val key: String) : IDataCenter
```

Interface injection can also be used as an SPI with a constructor function, but Interface Injection is more flexible.

Of course, components can also expose interfaces rather than specific objects:

```kotlin
class MainPageComponent {
    @Provides(IDataCenter::class)
    fun providesDataCenter(): DataCenter = xxx
}
```

### ViewModel Injection

Unlike regular constructor injection, ViewModel creation requires a special way and can only be created in specified classes (Activity, Fragment, Assem), so we designed this way:

```kotlin
class CustomVM @KnitViewModel constructor() : ViewModel()

class CustomAssemVM @KnitViewModel constructor() : AssemViewModel<MyState>()

class MainFragment : Fragment() {
    val customVM: CustomVM by knitViewModel()
    val customAssenVM: CustomAssemVM by assemViewModel()
}
```

Due to changing the creation method, it is necessary to use `knitViewModel` to create

> For assemViewModel, you can direct access to assemViewModel

### Multi-Provides

In some cases, there may be more than one provider of the type, in which case we can collect all of them through 3
methods: `IntoList/IntoSet/IntoMap`:

```kotlin
class ChildrenComponent {
    @Provides
    @IntoSet
    fun child1(): Child = TODO()

    @Provides
    @IntoSet
    @IntoList
    fun child2(): Child = TODO()
}

class MainPageComponent(
    val childrenComponent: ChildrenComponent
) {
    val children: Set<Child> by di// [child1, child2]
    val childrenList: List<Child> by di // [child2]
}
```

For `IntoMap`, you need to provide the corresponding `Pair`, and it will be automatically added to the corresponding Map according to the `Pair`'s generic types.

```kotlin
class ChildrenComponent {
    @Provides
    @IntoMap
    fun child1(): Pair<String, Child> = TODO()

    @Provides
    @IntoMap
    fun child2(): Pair<String, Child> = TODO()
}

class MainPageComponent(
    @Component val childrenComponent: ChildrenComponent
) {
    val childMap: Map<String, Child> by di
}
```

`@IntoList` may produce some duplicated elements in some cases, check [Principle of Knit Dependency Lookup](Principle_of_Dependency_Lookup.md) if you are interested for the details.

## Restrictions

Although the Knit framework greatly reduces the complexity of dependency injection , making it easy to inject any class and improving the flexibility of use, there are still some restrictions on its implementation.

### Interfaces

#### Annotations for properties in interface must be explicitly marked with @get

Kotlin supports the use of properties in the interface, but in essence, it still needs to use the get method behind it, so we need to explicitly add the corresponding annotation to its get method. The following is a specific example:

```kotlin
interface Foo {
    @get:Component val parentComponent: ParentComponent
    @get:Provides val str: String
}
```

#### Producers in the interface are not allowed to be marked as Singleton

The implementation behind `@Singleton` is to create a backing field in the current class and initialize it when the corresponding method or property getter is called for the first time. However, for interfaces it can not carry backing fields, so the provided elements can not be marked as `@Singleton`.

```kotlin
interface Foo {
    @Singleton // ❌ error
    @Provides
    fun str(): String
}

@Component
class FooComponent : Foo {
    override fun str(): String = "foo"
}
```

But we can do it by adding @Singleton to it at the implementation place, like following.

```kotlin
interface Foo {
    @Provides
    fun str(): String
}

@Component
class FooComponent : Foo {
    @Singleton // ✅ ok
    override fun str(): String = "foo"
}
```

### Generic types

1. Injection of ambiguous generic parameter types is not allowed.

    For regular generic types, it is possible to inject normally, but if your type contains ambiguous generic parameters, it cannot be injected, as follows:

    ```kotlin
    @Component
    class FooComponent<T> {
        val list: List<T> by di // ❌ error
    }
    ```

    In the above example, the `T` type is ambiguous, so it cannot be injected. Here is an example of a correct use case:

    ```kotlin
    @Component
    class FooComponent<T> {
        val list: List<String> by di // specific type
    }
    ```

2. Producer function constraints on generic parameters, not allowed to include generic parameters themselves. In the following example, the constraint on the generic parameter T is `List<T>`
    Because we want to match the inheritance relationship, and the circular inheritance relationship is difficult to determine and match at compile time, so we do not allow it.

    ```kotlin
    // ❌ error due to circular inheritance relationship of T
    @Provides fun <T : List<T>> foo(bar: T) : Foo<T>
    ```

### Kotlin only

Considering that the Kotlin coverage of our project is very high and Knit itself uses property delegate for injection, we do not consider supporting Java to avoid introducing unnecessary complexity; and all @Provides / @Component etc. annotations can only be added to the kotlin file.(no effects if you are trying to add such annotations inside of java files.)

#### Not available for local variables and methods

We cannot use by di, @Provides, and other Knit-related capabilities inside the function body, because Knit modifies the property getter, and it does not generate a corresponding getter for the by delegate syntax for local variables, so it is impossible to modify the return value of the property.

## Comparison with similar frameworks

Here are some comparisons when comparing Knit with other dependency injection frameworks like Dagger, Koin, Hilt, etc.

Pros:

1. Knit has better performance than other frameworks, it generates the bytecode directly ("knit" the code relationship), so it will get same performance as if you write it yourself, and even better efficiency than your directly written code.
2. Knit is much easier to use, it has a more concise syntax, and less redundant template code.
3. Knit has better Kotlin support like property delegate, we don't need `lateinit` or `by lazy`, it is naturally lazy initialization through `by di`.
4. Knit is more lightweight at runtime, only some marker code in the runtime library. Knit has no intermediation code generated when comparing with dagger or hilt, Knit generates compiled bytecode directly.
5. Knit is more flexible, it can inject everything to any classes, no restrictions on the scope of the injection, and no need to initialize the object by some special ways, just use its constructor directly.

Cons:

1. Knit didn't support Java. You need to make a Kotlin interop for Java for some legacy code.
2. Knit only supports JVM plaform beacuse it is based on bytecode manipulation, maybe it can be optimized if we can change the implementation to the modification of Kotlin IR in the future.

## Unit test

> Directly run `./gradlew knit-asm:test` if you are contributing to Knit project. This part is talking about how to test the code which uses Knit.

You can using following order to decide how to test your code:

1. DI framework is naturally easy to unit test by inject all things through constructor.
2. Using mockk or other mocking framework to mock the dependency.

Actually, we have implemented a unit test framework for Knit, which is based on run bytecode transformation at runtime, but it is very time costly because it needs to scan all runtime classpath to find all components, so we are still focused on how to optimize it and not published it yet.

## Principle and others

[Principle of Knit Dependency Lookup](Principle_of_Dependency_Lookup.md)
