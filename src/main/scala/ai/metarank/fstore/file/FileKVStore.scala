package ai.metarank.fstore.file

import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.{FeatureValue, Key}
import cats.effect.IO

import fs2.Stream

case class FileKVStore(db: FileClient, prefix: String, format: StoreFormat) extends KVStore[Key, FeatureValue] {
  override def put(values: Map[Key, FeatureValue]): IO[Unit] = IO {
    val size = values.size
    val keys = new Array[Array[Byte]](size)
    val vals = new Array[Array[Byte]](size)
    var i    = 0
    values.foreach(kv => {
      keys(i) = format.key.encode(prefix, kv._1).getBytes()
      vals(i) = format.featureValue.encode(kv._2)
      i += 1
    })
    db.put(keys, vals)
  }

  override def get(keys: List[Key]): IO[Map[Key, FeatureValue]] = for {
    array   <- IO(keys.map(k => format.key.encode(prefix, k).getBytes).toArray)
    bytes   <- IO(db.get(array))
    decoded <- IO.fromEither(decode(bytes))
  } yield {
    keys.zip(decoded).filter(_._2 != null).toMap
  }

  def decode(values: Array[Array[Byte]]): Either[Throwable, Array[FeatureValue]] = {
    val target = new Array[FeatureValue](values.length)
    var i      = 0
    var fail   = false
    while ((i < values.length) && !fail) {
      if (values(i) != null) {
        format.featureValue.decode(values(i)) match {
          case Left(err) => fail = true
          case Right(value) =>
            target(i) = value
        }
      }
      i += 1
    }
    if (fail) Left(new Exception("cannot decode feature value")) else Right(target)
  }
}

object FileKVStore {
  implicit val kvStateSource: StateSource[FeatureValue, FileKVStore] = new StateSource[FeatureValue, FileKVStore] {
    override def source(f: FileKVStore): fs2.Stream[IO, FeatureValue] =
      Stream
        .fromBlockingIterator[IO](f.db.firstN(s"${f.prefix}/".getBytes, Int.MaxValue), 128)
        .evalMap(kv => IO.fromEither(f.format.featureValue.decode(kv.value)))
  }
}
