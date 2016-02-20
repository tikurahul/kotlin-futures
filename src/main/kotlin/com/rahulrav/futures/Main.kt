package com.rahulrav.futures

/**
 * Basic tests on the Future.
 */
fun main(args: Array<String>) {
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
    println("Always $list, $exception")
  }
  val result: List<Int>? = joined.await(2000)
  println("The result is $result")

  Future.defaultExecutor().shutdown()

  val awaited = Future.timeOut(15000)
  val ignored = awaited.await(1000)
  println("The awaited result is $ignored")
}
