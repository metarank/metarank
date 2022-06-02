package ai.metarank.util.fs

import ai.metarank.config.MPath
import cats.effect.IO

trait FS[T <: MPath] {
  def read(path: T): IO[Array[Byte]]
  def write(path: T, bytes: Array[Byte]): IO[Unit]
  def listRecursive(path: T): IO[List[T]]
}

object FS {
  def read(path: MPath, env: Map[String, String]): IO[Array[Byte]] = path match {
    case s3: MPath.S3Path       => S3FS.resource(env).use(_.read(s3))
    case local: MPath.LocalPath => IO.pure(LocalFS()).flatMap(_.read(local))
  }

  def write(path: MPath, bytes: Array[Byte], env: Map[String, String]): IO[Unit] = path match {
    case s3: MPath.S3Path       => S3FS.resource(env).use(_.write(s3, bytes))
    case local: MPath.LocalPath => IO.pure(LocalFS()).flatMap(_.write(local, bytes))
  }

  def listRecursive(path: MPath, env: Map[String, String]): IO[List[MPath]] = path match {
    case s3: MPath.S3Path       => S3FS.resource(env).use(_.listRecursive(s3))
    case local: MPath.LocalPath => IO.pure(LocalFS()).flatMap(_.listRecursive(local))
  }
}
