package forex

package object common {
  type Cache[F[_], K, V] = cache.Algebra[F[_], K, V]
  final val Cache = cache.CaffeineCache
}
