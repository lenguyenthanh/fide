package fide
package broadcast
package test

import cats.effect.*
import cats.effect.kernel.Ref
import cats.effect.std.Random
import cats.effect.testkit.TestControl
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import scala.concurrent.duration.*

/** Design §11 retry tests. Uses TestControl for virtual time — no real sleeps. */
object RetryHelperSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def mkRandom: IO[Random[IO]] = Random.scalaUtilRandom[IO]

  private case class BoomRetryable(msg: String = "retryable") extends RuntimeException(msg)
  private case class BoomFatal(msg: String = "fatal")         extends RuntimeException(msg)

  private val retryable: Throwable => Boolean =
    case _: BoomRetryable => true
    case _                => false

  test("transient failures then success: completes after retries"):
    val program =
      for
        rnd    <- mkRandom
        given Random[IO] = rnd
        counter <- Ref.of[IO, Int](0)
        effect  = counter.update(_ + 1) *>
          counter.get.flatMap: n =>
            if n < 3 then IO.raiseError(BoomRetryable(s"attempt $n"))
            else IO.pure(42)
        result <- RetryHelper.retryOn[IO, Int](
          maxRetries = 3,
          baseDelay = 100.millis,
          logRetries = false
        )(retryable)(effect)
        attempts <- counter.get
      yield expect(result == 42) and expect(attempts == 3)
    TestControl.executeEmbed(program)

  test("permanent retryable: after budget exhausted, raises the error"):
    val program =
      for
        rnd    <- mkRandom
        given Random[IO] = rnd
        counter <- Ref.of[IO, Int](0)
        effect   = counter.update(_ + 1) *> IO.raiseError[Int](BoomRetryable())
        outcome <- RetryHelper.retryOn[IO, Int](
          maxRetries = 3,
          baseDelay = 100.millis,
          logRetries = false
        )(retryable)(effect).attempt
        attempts <- counter.get
      yield expect(outcome.isLeft) and expect(attempts == 4) // 1 initial + 3 retries
    TestControl.executeEmbed(program)

  test("non-retryable error: raised immediately, no retries"):
    val program =
      for
        rnd    <- mkRandom
        given Random[IO] = rnd
        counter <- Ref.of[IO, Int](0)
        effect   = counter.update(_ + 1) *> IO.raiseError[Int](BoomFatal())
        outcome <- RetryHelper.retryOn[IO, Int](
          maxRetries = 3,
          baseDelay = 100.millis,
          logRetries = false
        )(retryable)(effect).attempt
        attempts <- counter.get
      yield expect(outcome.isLeft) and expect(attempts == 1)
    TestControl.executeEmbed(program)

  test("success on first attempt: no retry cost"):
    val program =
      for
        rnd    <- mkRandom
        given Random[IO] = rnd
        counter <- Ref.of[IO, Int](0)
        effect   = counter.update(_ + 1).as(99)
        result  <- RetryHelper.retryOn[IO, Int](
          maxRetries = 3,
          baseDelay = 100.millis,
          logRetries = false
        )(retryable)(effect)
        attempts <- counter.get
      yield expect(result == 99) and expect(attempts == 1)
    TestControl.executeEmbed(program)
