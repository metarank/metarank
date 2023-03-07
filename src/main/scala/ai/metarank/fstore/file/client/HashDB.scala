package ai.metarank.fstore.file.client

trait HashDB extends DB[Array[Byte]] {
  def put(keys: Array[String], values: Array[Array[Byte]]): Unit
  def get(keys: Array[String]): Array[Array[Byte]]
}
