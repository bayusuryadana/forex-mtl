package forex.common.httpclient

import forex.common.httpclient.errors.Error

trait Algebra[F[_]] {
  def getAsString(url: String, headers: Map[String, String]): F[Error Either String]
}
