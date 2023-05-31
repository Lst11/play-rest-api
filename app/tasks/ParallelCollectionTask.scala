package tasks

import play.api.Logging

import scala.annotation.tailrec
import scala.collection.parallel.immutable.ParRange

object ParallelCollectionTask extends Logging{

  def factorial(n: Int): Int = n match {
    case 0 => 1
    case _ => n * factorial(n - 1)
  }

  def executeWithRaceCondition(): Unit = {
    import scala.collection.parallel.CollectionConverters._

    var i = 0
    for (j <- (1 to 1_000_000)) i += 1
    println(i)
  }

  /**
   * https://docs.scala-lang.org/overviews/parallel-collections/overview.html
   * https://docs.scala-lang.org/overviews/parallel-collections/configuration.html
   *
   * Available parallel collections:
   * ParArray
   * ParVector
   * mutable.ParHashMap
   * mutable.ParHashSet
   * immutable.ParHashMap
   * immutable.ParHashSet
   * ParRange
   * ParTrieMap (collection.concurrent.TrieMaps are new in 2.10)
   *
   * Collections that are inherently sequential (in the sense that the elements must be accessed one after the other),
   * like lists, queues, and streams, are converted to their parallel counterparts by copying the elements into a similar parallel collection.
   * An example is List– it’s converted into a standard immutable parallel sequence, which is a ParVector
   */
  def execute(): Unit = {
    import scala.collection.parallel.CollectionConverters._

    val par: ParRange = (1 to 50).par
    for (i <- par) {
      logger.info(s" current thread: ${Thread.currentThread().getName}" )
      println(i)
    }
  }

  def main(args: Array[String]): Unit = {
    execute()
  }
}
