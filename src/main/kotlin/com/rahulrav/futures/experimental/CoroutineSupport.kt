package com.rahulrav.futures.experimental

import com.rahulrav.futures.Future
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

/**
 * Adds support for co-routines.
 */
object CoroutineSupport {
  /**
   * Add the ability to suspend the {@link Future} type.
   */
  suspend fun <R> Future<R>.await(): R {
    return suspendCoroutine { continuation: Continuation<R> ->
      onSuccess { result: R ->
        continuation.resume(result)
      }
      onError { error: Exception ->
        continuation.resumeWithException(error)
      }
    }
  }
}
