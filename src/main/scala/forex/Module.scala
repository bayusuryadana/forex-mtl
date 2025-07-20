package forex

import cats.effect.{ Concurrent, Timer }
import forex.common.cache.CaffeineCache
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig, httpClient: Client[F]) {

  val cache: CaffeineCache[F, Rate.Pair, Rate] =
    CaffeineCache[F, Rate.Pair, Rate](maxSize = 1000, ttlSeconds = 60)

  private val ratesService: RatesService[F] = RatesServices[F](httpClient, cache, config.oneFrame.apiToken)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
