import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, Timer}
import forex.http.rates.Protocol.GetApiResponse
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import cats.implicits.{catsSyntaxParallelTraverse1, toFoldableOps}
import forex.domain.Currency
import forex.domain.Currency.{EUR, USD}
import org.http4s.implicits.http4sLiteralsSyntax

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Random


class IntegrationAndLoadSpec extends AnyFunSuite with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  implicit val ce: ConcurrentEffect[IO] = IO.ioConcurrentEffect(cs)

  test("GET /rates returns valid response") {

    val uri = Uri.unsafeFromString("http://localhost:8888/rates?from=USD&to=EUR")

    val test = BlazeClientBuilder[IO](ExecutionContext.global).resource.use { client =>
      client.expect[String](uri).map { body =>
        parse(body).flatMap(_.as[GetApiResponse]) match {
          case Right(rate) =>
            rate.from shouldBe USD
            rate.to shouldBe EUR
            rate.price.value should be > BigDecimal(0)
          case Left(err) =>
            fail(s"Failed to parse response: $err")
        }
      }
    }

    test.unsafeRunSync()
  }

  test("not failing 10k call") {

    val baseUri = uri"http://localhost:8888/rates"
    val random = new Random()
    val all = Currency.all

    def randomPair(): (String, String) = {
      val from = all(random.nextInt(all.length)).toString
      var to = ""
      do {
        to = all(random.nextInt(all.length)).toString
      } while (to == from)
      (from, to)
    }

    val test = BlazeClientBuilder[IO](ExecutionContext.global)
      .withMaxTotalConnections(1500)
      .withMaxWaitQueueLimit(1500)
      .resource.use { client =>
      def oneRequest(i: Int): IO[Unit] = {
        val (from, to) = randomPair()
        val reqUri = baseUri.withQueryParam("from", from).withQueryParam("to", to)
        client.get(reqUri) { res =>
          if (res.status.code == 200) IO.unit
          else IO.raiseError(new RuntimeException(s"Failed on request #$i: ${res.status} for $from->$to"))
        }
      }

      def oneBatch(round: Int): IO[Unit] = for {
        _ <- IO(println(s"Batch $round"))
        _ <- (1 to 1000).toList.parTraverse(oneRequest)
      } yield ()

      val rounds = (1 to 10).toList.traverse_ { round =>
        oneBatch(round) *> IO.sleep(1.second)
      }

      rounds.as(ExitCode.Success)
    }

    test.unsafeRunSync()
  }


}
