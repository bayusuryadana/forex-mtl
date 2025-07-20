package forex.config

import org.http4s.Uri
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameConfig(
    apiToken: String,
    host: Uri,
    cache: OneFrameCacheConfig
)

case class OneFrameCacheConfig(
    maxSize: Int,
    ttlSeconds: Int
)

object ApplicationConfig {
  implicit val uriConfigReader: ConfigReader[Uri] =
    ConfigReader[String].emap { str =>
      Uri.fromString(str).left.map(err => CannotConvert(str, "Uri", err.message))
    }
}
