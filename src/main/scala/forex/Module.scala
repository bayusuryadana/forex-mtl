package forex

import cats.data.Kleisli
import cats.effect.{Concurrent, Sync, Timer}
import cats.implicits.catsSyntaxApplicativeError
import forex.common._
import forex.common.cache.CaffeineCache
import forex.common.error.Error.CurrencyNotSupportedException
import forex.common.httpclient.Http4sClient
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig, http4sClient: Client[F]) {

  val httpClient: HttpClient[F] = Http4sClient(http4sClient)

  val cache: CaffeineCache[F, Rate.Pair, Rate] =
    CaffeineCache[F, Rate.Pair, Rate](maxSize = 1000, ttlSeconds = 60)

  private val ratesService: RatesService[F] =
    RatesServices[F](httpClient, cache, config.oneFrame.host, config.oneFrame.apiToken)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  private type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  private type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private def decodeFailureHandler[Func[_]: Sync]: HttpApp[Func] => HttpApp[Func] = { httpApp =>
    Kleisli { req =>
      httpApp.run(req).handleErrorWith {
        case cns: CurrencyNotSupportedException =>
          Sync[Func].delay {
            Response[Func](Status.BadRequest)
              .withEntity(s"Invalid request: ${cns.msg}")
          }
        case other =>
          Sync[Func].raiseError(other)
      }
    }
  }

  private val timeoutMiddleware: TotalMiddleware = { http =>
    Timeout(config.http.timeout)(http)
  }

  private val appMiddleware: TotalMiddleware =
    decodeFailureHandler andThen timeoutMiddleware

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
