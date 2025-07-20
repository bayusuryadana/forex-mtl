package forex.services.oneframe

import cats.effect.Async
import cats.syntax.all._
import forex.common.cache.CaffeineCache
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.oneframe.Protocol._
import forex.services.oneframe.errors.Error.OneFrameLookupFailed
import forex.services.oneframe.errors._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.client.Client
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIString

class Service[F[_]: Async](
    httpClient: Client[F],
    cache: CaffeineCache[F, Rate.Pair, Rate],
    host: Uri,
    apiToken: String
) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair).flatMap {
      case Some(cachedRate) =>
        Async[F].pure(Right(cachedRate))

      case None =>
        val allCounterpartsCurr: Seq[String] = Currency.all.flatMap { to =>
          List(
            s"${pair.from.show}${to.show}",
            s"${to.show}${pair.from.show}"
          )
        }

        val uri = (host / "rates")
          .withQueryParam("pair", allCounterpartsCurr)

        val request = Request[F](
          method = Method.GET,
          uri = uri,
          headers = Headers(Header.Raw.apply(CIString("token"), apiToken))
        )

        httpClient.expect[List[OneFrameResponse]](request).attempt.flatMap {
          case Right(responses) =>
            val putAll: F[Unit] = responses.traverse_ { r =>
              val p = Rate.Pair(Currency.fromString(r.from), Currency.fromString(r.to))
              val rate = Rate(p, Price(r.price), Timestamp(r.timestamp))
              cache.put(p, rate)
            }

            putAll *> {
              responses.find(r => r.from == pair.from.show && r.to == pair.to.show) match {
                case Some(found) =>
                  val rate = Rate(pair, Price(found.price), Timestamp(found.timestamp))
                  cache.put(pair, rate) *> Async[F].pure(Right(rate))

                case None =>
                  Async[F].pure(Left(OneFrameLookupFailed(s"Pair not found: $pair")))
              }
            }

          case Left(e) =>
            Async[F].pure(Left(OneFrameLookupFailed(s"Something is wrong: ${e.getMessage}")))
        }
    }

}

object Service {

  def apply[F[_]: Async](
      httpClient: Client[F],
      cache: CaffeineCache[F, Rate.Pair, Rate],
      host: Uri,
      apiToken: String
  ): Algebra[F] =
    new Service[F](httpClient, cache, host, apiToken)

}
