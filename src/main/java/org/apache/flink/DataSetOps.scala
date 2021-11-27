package org.apache.flink

object DataSetOps {
  implicit class DatasetScalaToJava[T](self: org.apache.flink.api.scala.DataSet[T]) {
    def toJava: org.apache.flink.api.java.DataSet[T] = self.javaSet
  }
}
