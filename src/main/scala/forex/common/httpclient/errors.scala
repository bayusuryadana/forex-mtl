package forex.common.httpclient

object errors {

  sealed trait Error
  object Error {
    final case class Http4sError(msg: String) extends Error
  }

}
