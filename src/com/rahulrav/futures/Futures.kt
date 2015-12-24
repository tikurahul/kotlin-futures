package com.rahulrav.futures

import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A really simple implementation of a Future.
 */
public class Future<R> {

  @Volatile var ready: Boolean = false
  @Volatile var result: R? = null
  @Volatile var error: Exception? = null

  private var executor: Executor? = null
  private val callbacks: ArrayList<Pair<(R) -> Unit, Boolean>> = ArrayList()
  private val errorBacks: ArrayList<Pair<(Exception) -> Unit, Boolean>> = ArrayList()
  private val alwaysCallbacks: ArrayList<Pair<(R?, Exception?) -> Unit, Boolean>> = ArrayList()
  private val lock: ReentrantLock = ReentrantLock()

  /**
   * Creates a {@link Future} with an unresolved state.
   */
  constructor(executor: Executor) : this(null, null, executor)

  /**
   * Creates a {@link Future} with a successful resolved state.
   */
  constructor(executor: Executor, result: R) : this(result, null, executor)

  /**
   * Creates a {@link Future} with a failed resolved state.
   */
  constructor(executor: Executor, error: Exception) : this(null, error, executor)

  private constructor(result: R?, error: Exception?, executor: Executor) {
    init(result, error, executor)
  }

  private fun init(result: R?, error: Exception?, executor: Executor) {
    this.executor = executor
    if (result != null) {
      this.result = result
      ready = true
    } else if (error != null) {
      this.error = error
      ready = true
    }
  }

  private fun onFulfilled() {
    lock.withLock {
      if (ready && result != null) {
        callbacks.forEachIndexed { i, pair ->
          val block = pair.first
          // only submit blocks that have not been executed before
          if (!pair.second) {
            executor?.execute {
              block.invoke(result!!)
            }
            val newPair = Pair(block, true)
            callbacks[i] = newPair
          }
        }
      }
    }
  }

  private fun onRejected() {
    lock.withLock {
      if (ready && error != null) {
        errorBacks.forEachIndexed { i, pair ->
          val block = pair.first
          // only submit blocks that have not been executed before
          if (!pair.second) {
            executor?.execute {
              block.invoke(error!!)
            }
            val newPair = Pair(block, true)
            errorBacks[i] = newPair
          }
        }
      }
    }
  }

  private fun onCompleted() {
    lock.withLock {
      if (ready) {
        alwaysCallbacks.forEachIndexed { i, pair ->
          val block = pair.first
          // only submit blocks that have not been executed before
          if (!pair.second) {
            executor?.execute {
              block.invoke(result, error)
            }
            val newPair = Pair(block, true)
            alwaysCallbacks[i] = newPair
          }
        }
      }
    }
  }

  /**
   * Adds a callback that will be executed on the successful completion of the {@link Future}.
   */
  public fun onSuccess(block: (R) -> Unit) {
    callbacks.add(Pair(block, false))
    onFulfilled()
  }

  /**
   * Adds a callback that will be executed on either successful / failure of the {@link Future}.
   */
  public fun always(block: (R?, Exception?) -> Unit) {
    alwaysCallbacks.add(Pair(block, false))
    onCompleted()
  }

  /**
   * Adds an error back which will be executed when the {@link Future} is marked as a failure.
   */
  public fun onError(block: (Exception) -> Unit) {
    errorBacks.add(Pair(block, false))
    onRejected()
  }

  /**
   * Marks the successful completion of the {@link Future}.
   */
  public fun resolve(result: R) = resolve(result, executor!!)

  /**
   * Marks the successful completion of the {@link Future} on the given {@link Executor}.
   */
  public fun resolve(result: R, executor: Executor) = complete(result, null, executor)

  /**
   * Marks the result of the {@link Future} as a failure.
   */
  public fun reject(error: Exception) = reject(error, executor!!)

  /**
   * Marks the result of the {@link Future} as a failure on the given {@link Executor}.
   */
  public fun reject(error: Exception, executor: Executor) = complete(null, error, executor)

  private fun complete(result: R?, error: Exception?, executor: Executor) {
    init(result, error, executor)
    if (result != null) {
      onFulfilled()
    } else if (error != null) {
      onRejected()
    }
    if (result != null || error != null) {
      onCompleted()
    }
  }

  /**
   * Helps with transformations on {@link Future}'s.
   */
  public fun <U> map(block: (R) -> U): Future<U> {
    return map(executor!!, block)
  }

  /**
   * Helps with transformations on {@link Future}'s.
   */
  public fun <U> map(executor: Executor, block: (R) -> U): Future<U> {
    val future: Future<U> = Future(executor)
    this.onSuccess { r: R ->
      try {
        executor.execute {
          future.resolve(block.invoke(r))
        }
      } catch (exception: Exception) {
        executor.execute {
          future.reject(exception)
        }
      }
    }
    this.onError { error ->
      executor.execute {
        future.reject(error)
      }
    }
    return future
  }

  /**
   * Helps in chaining asynchronous computations with {@link Future}'s.
   */
  public fun <U> flatMap(block: (R) -> Future<U>): Future<U> {
    return flatMap(executor!!, block)
  }

  /**
   * Helps in chaining asynchronous computations with {@link Future}'s.
   */
  public fun <U> flatMap(executor: Executor, block: (R) -> Future<U>): Future<U> {
    val future: Future<U> = Future(executor)
    this.onSuccess { r ->
      try {
        executor.execute {
          val futureU = block.invoke(r)
          futureU.onSuccess { u ->
            future.resolve(u)
          }
        }
      } catch (exception: Exception) {
        executor.execute {
          future.reject(exception)
        }
      }
    }
    this.onError { exception ->
      executor.execute {
        future.reject(exception)
      }
    }
    return future
  }

  /**
   * Waits to the future to resolve, and returns the result if available.
   */
  public fun await(timeout: Long): R? {
    if (ready) {
      if (result != null) {
        return result
      } else {
        return null;
      }
    }
    // wait for results
    val latch = CountDownLatch(1)
    val future = Future.timeOut(executor!!, timeout)
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
    public fun <R> join(executor: Executor, vararg f: Future<R>): Future<List<R>> {
      var joined = Future<List<R>>(executor)
      var results = ArrayList<R>(f.size)
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
    public fun <R> submit(executor: Executor, block: () -> R): Future<R> {
      val future = Future<R>(executor)
      executor.execute {
        try {
          val result = block.invoke()
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
      val future = Future<Unit>(executor)
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