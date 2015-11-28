package com.rahulrav.futures

import java.util.concurrent.Executors

/**
 * Basic tests on the Future.
 */
fun main(args: Array<String>) {
  val executor = Executors.newFixedThreadPool(10)
  val future = Future.submit(executor, {
    Thread.sleep(1000)
    10
  })
  val additive: Future<Int> = future.map { it.plus(10) }
  val multiplicative: Future<Int> = future.flatMap { Future(it * 100) }
  val joined: Future<List<Int>> = Future.join(additive, multiplicative)
  val result: List<Int>? = joined.await(executor, 2000)
  println("The result is $result")
  executor.shutdown()
}
