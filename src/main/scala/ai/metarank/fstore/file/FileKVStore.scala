package ai.metarank.fstore.file

import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.{FileClient, HashDB}
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.{FeatureValue, Key}
import cats.effect.IO
import fs2.Stream

case class FileKVStore(db: HashDB[Array[Byte]], format: StoreFormat) extends KVStore[Key, FeatureValue] {
  override def put(values: Map[Key, FeatureValue]): IO[Unit] = IO {
    val size = values.size
    val keys = new Array[String](size)
    val vals = new Array[Array[Byte]](size)
    var i    = 0
    values.foreach(kv => {
      keys(i) = format.key.encodeNoPrefix(kv._1)
      vals(i) = format.featureValue.encode(kv._2)
      i += 1
    })
    db.put(keys, vals)
  }

  override def get(keys: List[Key]): IO[Map[Key, FeatureValue]] = for {
    array   <- IO(keys.map(k => format.key.encodeNoPrefix(k)).toArray)
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
        .fromBlockingIterator[IO](f.db.all(), 128)
        .evalMap(kv => IO.fromEither(f.format.featureValue.decode(kv._2)))
  }
}
