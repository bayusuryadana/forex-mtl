package forex.services.oneframe

import cats.effect.Async
import cats.syntax.all._
import forex.common.cache.CaffeineCache
import forex.domain.{ Price, Rate, Timestamp }
import forex.services.oneframe.Protocol._
import forex.services.oneframe.errors.Error.OneFrameLookupFailed
import forex.services.oneframe.errors._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.client.Client
import org.http4s.{ Header, Headers, Method, Request }
import org.http4s.implicits._

class Service[F[_]: Async](
    httpClient: Client[F],
    cache: CaffeineCache[F, Rate.Pair, Rate],
    apiToken: String
) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair).flatMap {
      case Some(cachedRate) =>
        Async[F].pure(Right(cachedRate))

      case None =>
        val uri = uri"http://localhost:8080/rates"
          .withQueryParam("pair", s"${pair.from.show}${pair.to.show}")

        val request = Request[F](
          method = Method.GET,
          uri = uri,
          headers = Headers(Header("token", apiToken))
        )

        httpClient.expect[List[OneFrameResponse]](request).attempt.flatMap {
          case Right(responses) =>
            responses.find(r => r.from == pair.from.show && r.to == pair.to.show) match {
              case Some(found) =>
                val rate = Rate(pair, Price(found.price), Timestamp(found.timestamp))
                cache.put(pair, rate) *> Async[F].pure(Right(rate))

              case None =>
                Async[F].pure(Left(OneFrameLookupFailed(s"Pair not found: $pair")))
            }

          case Left(e) =>
            Async[F].pure(Left(OneFrameLookupFailed(e.getMessage)))
        }
    }

}

object Service {

  def apply[F[_]: Async](
      httpClient: Client[F],
      cache: CaffeineCache[F, Rate.Pair, Rate],
      apiToken: String
  ): Algebra[F] =
    new Service[F](httpClient, cache, apiToken)

}
