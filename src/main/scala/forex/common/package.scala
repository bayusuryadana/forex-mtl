package forex

package object common {
  type Cache[F[_], K, V] = cache.Algebra[F, K, V]
  final val Cache = cache.CaffeineCache

  type HttpClient[F[_]] = httpclient.Algebra[F]
  final val httpClient = httpclient.Http4sClient
}
