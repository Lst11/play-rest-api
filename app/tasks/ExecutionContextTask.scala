package tasks

import akka.actor.ActorSystem
import play.api.Logging
import tasks.ParallelCollectionTask.logger

import java.util.concurrent._
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}


object FixedThreadPool {
  def apply(nThreads: Int, name: String, daemon: Boolean = true): ThreadPoolExecutor =
    new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue[Runnable], new NamedThreadFactory(name, daemon))
}

class NamedThreadFactory(val name: String, daemon: Boolean, suppressExceptions: Boolean = false) extends ThreadFactory {
  private var counter = 0

  def newThread(r: Runnable) = this.synchronized {

    val thread = new Thread(r, name + "-" + counter) {
      override def run() {
        try {
          r.run()
        } catch {
          case e: Exception =>
            if (!suppressExceptions) throw e
        }
      }
    }

    counter += 1
    if (daemon)
      thread.setDaemon(true)
    thread
  }
}

object ExecutionContextTask extends Logging {

  def calculate(n: Int)(implicit executor: ExecutionContext) = Future {
    logger.info(s" current thread 2: ${Thread.currentThread().getName}")
    ParallelCollectionTask.factorial(n)
  }

  /**
   * 1. Get execution context (e.g. actorSystem.dispatcher)
   * 2. Do a future value computation
   * 3. Do smth while computation is running on another thread
   * 4. map or flatMap future results.
   */
  def executeMapExample(): Future[Int] = {
    implicit val ec = ExecutionContext.Implicits.global // will create as many tasks as cpus

    val futureResult: Seq[Future[Int]] = Seq.range(1, 10, 2).map(calculate)
    logger.info("Computation started")
    val resultAsFuture: Future[Seq[Int]] = Future.sequence(futureResult)

    resultAsFuture.map(_.length)
  }

  def executeWithFixedThreadPool(): Unit = {
    implicit val ec = ExecutionContext fromExecutorService Executors.newFixedThreadPool(5)

    try {
      val futureResult: Seq[Future[Int]] = Seq.range(1, 10, 2).map(calculate)
      logger.info("Computation started")

      futureResult.foreach(res => logger.info(s"computation completed: $res"))
    } finally {
      //       the execution context has to be shut down explicitly
      ec.shutdown()
    }
  }

  /**
   * Using flatMap the result will be just flattened from Future[Future[T]] to Future [T])
   */
  def executeFlatMapExample(): Unit = {
    implicit val ec = ExecutionContext.fromExecutor(FixedThreadPool(10, "non-blocking-io"))

    // each new future is created once the previous one is completed
    //    val futureResult: Future[Int] = {
    //      calculate(1).flatMap { a =>
    //        calculate(a + 1).flatMap { b =>
    //          calculate(b + 1).flatMap { c =>
    //            calculate(c + 1).map { d =>
    //              a + b + c + d
    //            }
    //          }
    //        }
    //      }
    //    }

    val futureResult: Future[Int] = for {
      a <- calculate(1)
      b <- calculate(a + 1)
      c <- calculate(b + 1)
      d <- calculate(c + 1)
    } yield {
      a + b + c + d
    }

    val result = Await.result(futureResult, 30.seconds)
    logger.info(s"result of the computation is: $result")
  }

  /**
   * 1. Default context for play:  import play.core.Execution.Implicits.internalContext.
   * Play context is never used in the application code.
   * Its size can be configured by setting internal-threadpool-size in application.conf, and it defaults to the number of available processors.
   *
   * 2. Play provides a way to configure custom dispatcher using application.conf.
   * Note: Several Akka actors can share the same thread.
   * An ActorSystem which is used in order to access it is a hierarchical group of actors which share common configuration,
   * e.g. dispatchers, deployments, remote capabilities and addresses. It is also the entry point for creating or looking up actors.
   *
   * 3. scala.concurrent.ExecutionContext.Implicits.global mustn't be used with play,
   * since the context from the Scala standard library doesn't provide any control over it neither for Play or developer.
   * It also has the potential to spawn a lot of threads and use a ton of memory, if you're not careful.
   */

  def executeWithCustomContext(): Unit = {
    import scala.collection.parallel.CollectionConverters._

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher
    implicit val ec = system.dispatchers.lookup("custom-dispatcher")



    val futureResult = (Seq(1).par) map calculate
    futureResult.foreach(res => logger.info(s" current thread 1: ${Thread.currentThread().getName}"))

    //Await.result(futureResult, Duration.Inf)
  }


  def main(args: Array[String]): Unit = {
    executeWithCustomContext()
  }
}
