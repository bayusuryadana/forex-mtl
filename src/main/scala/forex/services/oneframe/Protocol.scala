package forex.services.oneframe

import cats.syntax.either._
import io.circe._

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object Protocol {

  final case class OneFrameResponse(
      from: String,
      to: String,
      bid: BigDecimal,
      ask: BigDecimal,
      price: BigDecimal,
      timestamp: OffsetDateTime
  )

  implicit val decodeOffsetDateTime: Decoder[OffsetDateTime] = Decoder.decodeString.emap { str =>
    Either
      .catchNonFatal(OffsetDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      .leftMap(t => s"OffsetDateTime parsing error: ${t.getMessage}")
  }

  implicit val decoder: Decoder[OneFrameResponse] =
    Decoder.forProduct6("from", "to", "bid", "ask", "price", "time_stamp")(OneFrameResponse.apply)

}
