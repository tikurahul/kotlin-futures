package com.rahulrav.futures

import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/**
 * A really simple implementation of a Future.
 */
public class Future<R> {

  private var ready: Boolean = false
  private var result: R? = null
  private var error: Exception? = null

  private var callbacks: List<Pair<(R) -> Unit, Boolean>> = LinkedList()
  private var errorBacks: List<Pair<(Exception) -> Unit, Boolean>> = LinkedList()

  /**
   * Creates a {@link Future} with an unresolved state.
   */
  constructor() : this(null, null)

  /**
   * Creates a {@link Future} with a successful resolved state.
   */
  constructor(result: R) : this(result, null)

  /**
   * Creates a {@link Future} with a failed resolved state.
   */
  constructor(error: Exception) : this(null, error)

  private constructor(result: R?, error: Exception?) {
    init(result, error)
  }

  private fun init(result: R?, error: Exception ?) {
    if (result != null) {
      this.result = result
      ready = true
    } else if (error != null) {
      this.error = error
      ready = true
    }
  }

  private fun onFulfilled() {
    val postCallbacks = LinkedList<Pair<(R) -> Unit, Boolean>>()
    callbacks.forEach { pair ->
      val block = pair.first
      var executed = false
      // only execute blocks that have not been executed before
      if (!pair.second) {
        try {
          if (ready) {
            block.invoke(result!!)
            executed = true
          }
        } catch (ignore: Exception) {
        }
      }
      val newPair = Pair(block, executed)
      postCallbacks += newPair
    }
    callbacks = postCallbacks
  }

  private fun onRejected() {
    val postErrorBacks = LinkedList<Pair<(Exception) -> Unit, Boolean>>()
    errorBacks.forEach { pair ->
      val block = pair.first
      var executed = false
      // only execute blocks that have not been executed before
      if (!pair.second) {
        try {
          if (ready) {
            block.invoke(error!!)
            executed = true
          }
        } catch (ignore: Exception) {

        }
      }
      val newPair = Pair(block, executed)
      postErrorBacks += newPair
    }
    errorBacks = postErrorBacks
  }

  /**
   * Adds a callback that will be executed on the successful completion of the {@link Future}.
   */
  public fun onSuccess(block: (R) -> Unit) {
    callbacks += Pair(block, false)
    onFulfilled()
  }

  /**
   * Adds an error back which will be executed when the {@link Future} is marked as a failure.
   */
  public fun onError(block: (Exception) -> Unit) {
    errorBacks += Pair(block, false)
    onRejected()
  }

  /**
   * Marks the successful completion of the {@link Future}.
   */
  public fun resolve(result: R) = complete(result, null)

  /**
   * Marks the result of the {@link Future} as a failure.
   */
  public fun reject(error: Exception) = complete(null, error)

  private fun complete(result: R?, error: Exception?) {
    init(result, error)
    if (result != null) {
      onFulfilled()
    } else if (error != null) {
      onRejected()
    }
  }

  /**
   * Helps with transformations on {@link Future}'s.
   */
  public fun <U> map(block: (R) -> U): Future<U> {
    val future: Future<U> = Future()
    this.onSuccess { r: R ->
      try {
        future.resolve(block.invoke(r))
      } catch (exception: Exception) {
        future.reject(exception)
      }
    }
    this.onError { error ->
      future.reject(error)
    }
    return future
  }

  /**
   * Helps in chaining asynchronous computations with {@link Future}'s.
   */
  public fun <U> flatMap(block: (R) -> Future<U>): Future<U> {
    val future: Future<U> = Future()
    this.onSuccess { r ->
      try {
        val futureU = block.invoke(r)
        futureU.onSuccess { u ->
          future.resolve(u)
        }
      } catch (exception: Exception) {
        future.reject(exception)
      }
    }
    this.onError { exception ->
      future.reject(exception)
    }
    return future
  }

  /**
   * Waits to the future to resolve, and returns the result if available.
   */
  public fun await(executor: Executor, timeout: Long): R? {
    if (ready) {
      if (result != null) {
        return result
      } else {
        return null;
      }
    }
    // wait for results
    val latch = CountDownLatch(1)
    val future = Future.timeOut(executor, timeout)
    future.onSuccess {
      latch.countDown()
    }
    future.onError {
      latch.countDown()
    }
    latch.await()
    if (ready && result != null) {
      return result
    } else {
      return null;
    }
  }

  companion object {

    /**
     * Returns a composite Future, based on a variable list of Futures.
     */
    public fun <R> join(vararg f: Future<R>): Future<List<R>> {
      var joined = Future<List<R>>()
      var results = LinkedList<R>()
      val size = f.size
      val successCallback = { result: R ->
        results.add(result)
        if (results.size == size) {
          // we have all the results
          joined.resolve(results)
        }
      }
      val failureCallback = { exception: Exception ->
        // fail fast
        joined.reject(exception)
      }
      for (future in f) {
        future.onSuccess(successCallback)
        future.onError(failureCallback)
      }
      return joined
    }

    /**
     * Submits a {@link Callable} to a {@link Executor} to produce a Future.
     */
    public fun <R> submit(executor: Executor, callable: Callable<R>): Future<R> {
      val future = Future<R>()
      executor.execute {
        try {
          val result = callable.call()
          future.resolve(result)
        } catch (exception: Exception) {
          future.reject(exception)
        }
      }
      return future
    }

    /**
     * Convenience methods to produce a {@link Future} that resolves after the given timeout.
     */
    public fun timeOut(executor: Executor, timeout: Long): Future<Unit> {
      val future = Future<Unit>()
      executor.execute {
        try {
          Thread.sleep(timeout)
          future.resolve(Unit)
        } catch (exception: Exception) {
          future.reject(exception)
        }
      }
      return future
    }

  }

}
