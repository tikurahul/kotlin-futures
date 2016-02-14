# Kotlin Futures

A simple implementation of Futures in Kotlin.
Has very minimal dependencies, so you can use it on Android.

### Getting Started

Creating a `Future` is really easy.

```kotlin
import com.rahulrav.futures.Future

val future: Future<Int> = Future.submit {
  Thread.sleep(10)
  10
}
```

### Support for combinators

This future supports combinators like `map` and `flatMap` so you can transform and flatten `Future`'s.

```kotlin
val response: Future<HttpResponse> = someLongRunningComputation()
val headers: Future<List<Pair<String, String>>> = response.map { result ->
  return result.headers
}
```

To flatten results of asynchronous computations use `flatMap`.

```kotlin
val first: Future<Int> = someLongRunningComputation()
val result: Future<Double> = first.flatMap { result ->
  val second: Future<Double> = anotherLongRunningComputation(result)
  // we are returning a Future<Double> instead of a Future<Future<Double>>
  return second
}
```

### Use on Android

To use this library on Android, all you need to do is to provide an implementation
of the `Executor` that uses a `Looper`. Here is an example.

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

### Download

[ ![Kotlin Futures 1.0](https://api.bintray.com/packages/rahulrav/kotlin-futures/com.rahulrav.futures/images/download.svg) ](https://bintray.com/rahulrav/kotlin-futures/com.rahulrav.futures/_latestVersion)
