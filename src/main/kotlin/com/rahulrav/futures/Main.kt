package com.rahulrav.futures

import com.rahulrav.futures.experimental.CoroutineSupport.await
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch


fun basicUses() {
  println("Testing basic usage of Futures.")
  val future = Future.submit {
    Thread.sleep(1000)
    10
  }

  val additive: Future<Int> = future.map { it.plus(10) }
  val multiplicative: Future<Int> = future.flatMap {
    Future.submit {
      it * 100
    }
  }
  val joined: Future<List<Int>> = Future.join(additive, multiplicative)
  joined.always { list, exception ->
    println("Always = Result ($list), Exception ($exception)")
  }
  val result: List<Int>? = joined.await(2000)
  println("The result is $result")

  val awaited = Future.timeOut(3000)
  val ignored = awaited.await(1000)
  println("The awaited result is $ignored") // should be null
}

fun couroutineUses() {
  val future = Future.submit {
    Thread.sleep(100)
    10
  }

  launch(CommonPool) {
    val result = future.await()
    println("Coroutine support: The result is $result")
  }
}


/**
 * Basic tests on the Future.
 */
fun main(args: Array<String>) {
  couroutineUses()
  basicUses()
}
