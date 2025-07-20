package forex

package object services {
  type RatesService[F[_]] = oneframe.Algebra[F]
  final val RatesServices = oneframe.Service
}
