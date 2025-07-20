package forex.common.cache

import cats.effect.Sync
import com.github.benmanes.caffeine.cache.{ Cache, Caffeine }
import java.util.concurrent.TimeUnit

class CaffeineCache[F[_]: Sync, K, V](maxSize: Long, ttlSeconds: Long, cache: Cache[K, V]) extends Algebra[F, K, V] {

  override def get(key: K): F[Option[V]] =
    Sync[F].delay(Option(cache.getIfPresent(key)))

  override def put(key: K, value: V): F[Unit] =
    Sync[F].delay(cache.put(key, value))

}

object CaffeineCache {
  def apply[F[_]: Sync, K, V](maxSize: Long, ttlSeconds: Long): CaffeineCache[F, K, V] = {
    val cache = Caffeine
      .newBuilder()
      .maximumSize(maxSize)
      .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
      .build[K, V]()

    new CaffeineCache[F, K, V](maxSize, ttlSeconds, cache)
  }
}
