package forex.services.oneframe

import cats.effect.Async
import cats.syntax.all._
import forex.common.{ Cache, HttpClient }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.oneframe.Protocol._
import forex.services.oneframe.errors.Error.OneFrameLookupFailed
import forex.services.oneframe.errors._
import io.circe.parser.decode

class Service[F[_]: Async](
    httpClient: HttpClient[F],
    cache: Cache[F, Rate.Pair, Rate],
    host: String,
    apiToken: String
) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair).flatMap {
      case Some(cachedRate) =>
        Async[F].pure(Right(cachedRate))

      case None =>
        val baseUrl = s"${host}/rates"

        val allPairs = Currency.all.flatMap { to =>
          Seq(s"${pair.from.show}${to.show}", s"${to.show}${pair.from.show}")
        }

        val queryString = allPairs.map(p => s"pair=$p").mkString("&")
        val urlString = s"$baseUrl?$queryString"
        httpClient.getAsString(urlString, Map("token" -> apiToken)).flatMap {
          case Left(err) => Async[F].pure(Left(OneFrameLookupFailed(s"Request error: $err")))
          case Right(body) =>
            handleResponse(body, pair)
        }
    }

  private def handleResponse(body: String, pair: Rate.Pair): F[Error Either Rate] = {
    decode[List[OneFrameResponse]](body) match {
      case Left(err) =>
        Async[F].pure(Left(OneFrameLookupFailed(s"JSON decode error: ${err.getMessage}")))

      case Right(responses) =>
        val putAll: F[Unit] = responses.traverse_ { r =>
          val p    = Rate.Pair(Currency.fromString(r.from), Currency.fromString(r.to))
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
    }
  }

}

object Service {

  def apply[F[_]: Async](
      httpClient: HttpClient[F],
      cache: Cache[F, Rate.Pair, Rate],
      host: String,
      apiToken: String
  ): Algebra[F] =
    new Service[F](httpClient, cache, host, apiToken)

}
