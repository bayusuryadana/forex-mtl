import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import forex.common.{Cache, HttpClient}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.common.httpclient.errors.Error
import forex.common.httpclient.errors.Error.Http4sError
import forex.services.oneframe.Protocol.OneFrameResponse
import forex.services.oneframe.Service
import forex.services.oneframe.errors.Error.OneFrameLookupFailed
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class ServiceSpec extends AnyFunSuite with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]     = IO.timer(ExecutionContext.global)

  val USD = Currency.USD
  val EUR = Currency.EUR

  val testHost  = "https://api.oneframe.com"
  val testToken = "secret-token"

  def pair(from: Currency, to: Currency): Rate.Pair = Rate.Pair(from, to)

  def rate(
            p: Rate.Pair,
            price: BigDecimal = 1.23,
            timestamp: OffsetDateTime = OffsetDateTime.parse("2023-01-01T00:00:00Z")
          ): Rate =
    Rate(p, Price(price), Timestamp(timestamp))

  def jsonResponseFromRates(rates: List[OneFrameResponse]): String =
    rates.map { r =>
      s"""{"from":"${r.from}","to":"${r.to}","bid":${r.bid},"ask":${r.ask},"price":${r.price},"time_stamp":"${r.timestamp}"}"""
    }.mkString("[", ",", "]")

  class MockCache(initialData: Map[Rate.Pair, Rate] = Map.empty) extends Cache[IO, Rate.Pair, Rate] {
    private val ref = Ref.unsafe[IO, Map[Rate.Pair, Rate]](initialData)
    override def get(key: Rate.Pair): IO[Option[Rate]]      = ref.get.map(_.get(key))
    override def put(key: Rate.Pair, value: Rate): IO[Unit] = ref.update(_ + (key -> value))
  }

  class MockHttpClient(response: Error Either String) extends HttpClient[IO] {
    override def getAsString(url: String, headers: Map[String, String]): IO[Error Either String] =
      IO.pure(response)
  }

  test("get returns cached rate when found in cache") {
    val pairToTest = pair(USD, EUR)
    val cachedRate = rate(pairToTest)

    val cache = new MockCache(Map(pairToTest -> cachedRate))
    val httpClient = new MockHttpClient(Left(Http4sError("Should not be called")))

    val service = new Service[IO](httpClient, cache, testHost, testToken)

    val io = service.get(pairToTest).map(_ shouldBe Right(cachedRate))
    io.unsafeRunSync()
  }

  test("get fetches from HTTP and caches on cache miss with successful response") {
    val pairToTest = pair(USD, EUR)
    val now = OffsetDateTime.parse("2023-01-01T00:00:00Z")

    val responses = List(
      OneFrameResponse("USD", "EUR", 1.10, 1.15, 1.12, now),
      OneFrameResponse("EUR", "USD", 0.88, 0.90, 0.89, now.plusSeconds(10))
    )

    val jsonResponse = jsonResponseFromRates(responses)

    val cache = new MockCache()
    val httpClient = new MockHttpClient(Right(jsonResponse))

    val service = new Service[IO](httpClient, cache, testHost, testToken)

    val io = for {
      res <- service.get(pairToTest)
      cachedUsdEur <- cache.get(pairToTest)
      cachedEurUsd <- cache.get(pair(EUR, USD))
    } yield {
      res match {
        case Right(rate) =>
          rate.price.value shouldBe 1.12
          rate.timestamp.value shouldBe now
        case _ => fail("Expected successful rate response")
      }
      cachedUsdEur.map(_.price.value) shouldBe Some(1.12)
      cachedEurUsd.map(_.price.value) shouldBe Some(0.89)
    }

    io.unsafeRunSync()
  }

  test("get returns error when JSON decoding fails") {
    val pairToTest = pair(USD, EUR)

    val brokenJson = """{ "not": "valid", """

    val cache = new MockCache()
    val httpClient = new MockHttpClient(Right(brokenJson))

    val service = new Service[IO](httpClient, cache, testHost, testToken)

    val io = service.get(pairToTest).map {
      case Left(_: OneFrameLookupFailed) => succeed
      case other                         => fail(s"Expected OneFrameLookupFailed due to decode error, got $other")
    }

    io.unsafeRunSync()
  }

  test("get returns error when HTTP returns pair not found") {
    val pairToTest = pair(USD, EUR)
    val now = OffsetDateTime.parse("2023-01-01T00:00:00Z")
    val jsonResponse = jsonResponseFromRates(List(
      OneFrameResponse("EUR", "GBP", 1.20, 1.25, 1.22, now)
    ))

    val cache = new MockCache()
    val httpClient = new MockHttpClient(Right(jsonResponse))

    val service = new Service[IO](httpClient, cache, testHost, testToken)

    val io = service.get(pairToTest).map {
      case Left(_: OneFrameLookupFailed) => succeed
      case other                         => fail(s"Expected OneFrameLookupFailed, got $other")
    }

    io.unsafeRunSync()
  }

  test("get returns error when HTTP client fails") {
    val pairToTest = pair(USD, EUR)

    val cache = new MockCache()
    val httpClient = new MockHttpClient(Left(Http4sError("HTTP failure")))

    val service = new Service[IO](httpClient, cache, testHost, testToken)

    val io = service.get(pairToTest).map {
      case Left(_: OneFrameLookupFailed) => succeed
      case other                         => fail(s"Expected OneFrameLookupFailed, got $other")
    }

    io.unsafeRunSync()
  }
}
