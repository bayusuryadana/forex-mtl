package forex.common

object error {

  sealed trait Error extends Exception
  object Error {
    final case class CurrencyNotSupportedException(msg: String) extends Error
  }

}
