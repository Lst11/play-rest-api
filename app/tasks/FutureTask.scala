package tasks

import play.api.Logging

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object FutureTask extends Logging {

  def printMsg() = Future(logger.info("future result"))

  def calculate(n: Int) = Future {
    ParallelCollectionTask.factorial(n)
  }

  def calculateWithException(n: Int) = Future {
    ParallelCollectionTask.factorial(n)
    throw new RuntimeException("Smth wrong")
  }

  /**
   * 1. There is no order in async requests
   */
  def execute(): Unit = {
    printMsg()
    //Thread.sleep(1000)
  }

  /**
   * 2. Ways of processing the results:
   *    a. foreach doesn't handle exceptions {@link Future}
   *       b. onComplete wrapper with Try
   *       c. {@link Await} - should be avoided, the Future value should be returned instead
   *       d. no need to wait for result with modern libraries (e.g. akka-http accepts a Future value inside an HTTP route)
   *       3. Typical mistakes:
   *       a. unnecessarily waiting for futures to complete - blocked threads
   *          b. forgetting to wait to complete - race conditions (better to not have a mutable state, or manage it with great care)
   *          c. expecting to see a stack trace from exceptions inside a Future
   *          d. using the same thread pool everywhere - thread starvation (better to have one for short tasks, and another for the rest)
   *          e. forgetting to shut down the thread pool - application never quits
   */
  def executeWithException() = {
    val futureResult = calculateWithException(10)
    val futureValue: Option[Try[Int]] = futureResult.value

    //futureResult.foreach(res => logger.info(s"futureResult: $res"))

    futureResult.onComplete {
      case Success(n) => logger.info(s"futureResult: $n")
      case Failure(exc) => logger.warn(s"failed with exception: ${exc.getMessage}")
    }
    //Thread.sleep(2000)

    //val result = Await.result(futureResult, Duration.Inf)
    val result = Await.result(futureResult, 2.seconds) //blocks the main thread

    //logger.info(s"futureResult: $result")
  }

  def executeWithPromise(): Unit = {
    def calculate[T](b: => T): Future[T] = {
      val promiseResult = Promise[T]
      global.execute(() => try {
        promiseResult.success(b)
      } catch {
        case NonFatal(e) => promiseResult.failure(e)
      })
      promiseResult.future
    }

    val f1: Future[String] = calculate {
      "promise " * 8 + " repeat!"
    }
    val f2: Future[Int] = calculate {
      5 * 5
    }
    val f3: Future[Int] = calculate {
      5 / 0
    }

    val results = Seq(f1, f2, f3)
    results.foreach(res => logger.info(s"result of the computation: $res"))

    Thread.sleep(1000)
  }

  def main(args: Array[String]): Unit = {
    logger.info("start")
    executeWithException()
    logger.info("end")
  }
}
