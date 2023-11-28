package ai.metarank.fstore.file.client

trait HashDB[T] extends DB[T] {
  def put(keys: Array[String], values: Array[T]): Unit
  def get(keys: Array[String]): Array[T]
}
