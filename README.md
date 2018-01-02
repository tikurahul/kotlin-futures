# Kotlin Futures

A simple implementation of Futures in Kotlin.
Has very minimal dependencies, so you can use it on Android.

### Getting Started

#### Acquiring using Gradle

Add my bintray repo as a maven repository:

```gradle
repositories {
  maven {
    url "https://dl.bintray.com/rahulrav/kotlin-futures/"
  }
}
```
and then add the library as a dependency:

```gradle
dependencies {
  compile "com.rahulrav:com.rahulrav.futures:1.1.6"
}
```

#### Creating Futures

Creating a `Future` is really easy.

```kotlin
import com.rahulrav.futures.Future

val future: Future<Int> = Future.submit {
  // fake some long running computation
  Thread.sleep(1000)
  10
}
```

### Support for combinators

This future supports combinators like `map` and `flatMap` so you can transform and flatten `Future`'s.

```kotlin
val response: Future<HttpResponse> = someLongRunningComputation()
val headers: Future<List<Pair<String, String>>> = response.map { result ->
  result.headers
}
```

To flatten results of asynchronous computations use `flatMap`.

```kotlin
val first: Future<Int> = someLongRunningComputation()
val result: Future<Double> = first.flatMap { result ->
  val second: Future<Double> = anotherLongRunningComputation(result)
  // we are returning a Future<Double> instead of a Future<Future<Double>>
  second
}
```

### Use on Android

Futures work on Android. However, one important thing to note is there might be operations that you might want to run on the UI thread instead of the default executor. In such cases, all you need to do is to provide an implementation
of the `Executor` that uses a `Looper`. Here is an example of how you could do that:

```kotlin
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

class DefaultExecutors {
  companion object {
    val handler = lazy {
      Handler(Looper.getMainLooper())
    }
    /** Submits things on the UI Thread. */
    val UiExecutor = Executor({ command ->
      handler.value.post(command)
    })
  }
}
```

When you want to execute a sub-task on the UI thread (using the executor defined above), you can then do something like:

```kotlin
val future: Future<Int> = Future.submit {
  // fake some long running computation
  Thread.sleep(1000)
  10
}

future.map(DefaultExecutors.UiExecutor) {
  // this sub-task now runs on the main looper
}
```

### Important Note

When using the Kotlin Futures library you need to be careful about returing `null` or optional values.
This is because, the implementation of the library expects the caller to provide non-null values.

<b>Warning: If you attempt to resolve a `Future` with a `null` value, it will never get resolved.</b>

You should wrap your optional type with something like:

```kotlin
sealed class Optional<T> {
  class Some<T>(val contents: T): Optional<T>()
  object None
}
```

If you are using JDK 8, you should use the [Optional](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html) type.

### Support for Coroutines

This library now has experimental support for [Kotlin Coroutines](https://kotlinlang.org/docs/reference/coroutines.html#generators-api-in-kotlincoroutines).
Here is an example on how you can use coroutines.


```kotlin
import com.rahulrav.futures.experimental.CoroutineSupport.await
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch

val future = Future.submit {
  Thread.sleep(100)
  10
}
 
launch(CommonPool) {
  val result = future.await()
  println("Coroutine support: The result is $result")
}
```

### Download

 [ ![Download](https://api.bintray.com/packages/rahulrav/kotlin-futures/com.rahulrav.futures/images/download.svg) ](https://bintray.com/rahulrav/kotlin-futures/com.rahulrav.futures/_latestVersion)
