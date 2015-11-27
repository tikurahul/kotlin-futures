# Kotlin Futures

A simple implementation of Futures in Kotlin.
Has very minimal dependencies, so you can use it on Android.

### Getting Started

Creating a `Future` is really easy.

```
import com.rahulrav.futures.Future

val executor: Executor = Executors.newFixedThreadPool(10)
val future: Future<Int> = Future.submit(executor, Callable<Int> {
  Thread.sleep(10);
  10;
});
```

### Support for combinators

This future supports combinators like `map` and `flatMap` so you can transform and flatten `Future`'s.

```
val response: Future<HttpResponse> = someLongRunningComputation()
val headers: Future<Pair<String, String>> = response.map { result =>
  return result.headers
}
```

To flatten results of asynchronous computations use `flatMap`.

```
  val first: Future<Int> = someLongRunningComputation();
  val result: Future<Double> = first.flatMap { result =>
    val second: Future<Double> = anotherLongRunningComputation(result);
	// we are returning a Future<Double> instead of a Future<Future<Double>>
    return second
  }
```

### Use on Android

To use this library on Android, all you need to do is to provide an implementation
of the `Executor` that uses a `Looper`.
