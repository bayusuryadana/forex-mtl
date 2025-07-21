package forex.common.httpclient

import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps}
import forex.common.httpclient.errors.Error
import org.http4s._
import org.http4s.client.Client
import org.typelevel.ci.CIString

class Http4sClient[F[_]: Async](client: Client[F]) extends Algebra[F] {
  def getAsString(url: String, headers: Map[String, String]): F[Error Either String] = {
    Uri.fromString(url) match {
      case Left(err) => Async[F].pure(Left(Error.Http4sError(s"Invalid URL: ${err.message}")))
      case Right(uri) =>
        val hdrs = Headers(headers.map { case (k,v) => Header.Raw(CIString(k), v) }.toList)
        val req = Request[F](Method.GET, uri, headers = hdrs)

        client.expect[String](req).attempt.map {
          case Right(body) => Right(body)
          case Left(e)     => Left(Error.Http4sError(e.getMessage))
        }
    }
  }
}

object Http4sClient {
  def apply[F[_]: Async](client: Client[F]): Http4sClient[F] =
    new Http4sClient[F](client)
}
