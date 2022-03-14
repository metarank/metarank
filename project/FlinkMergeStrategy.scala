import sbt._
import sbtassembly.MergeStrategy

import java.io.File
import scala.util.matching.Regex

/** A custom sbe merge strategy to remove ancient bundled aws-sdk and guava versions from flink-s3-fs-hadoop dependency.
  * @param jar
  * @param exclude
  */
case class FlinkMergeStrategy(jar: Regex, exclude: List[String]) extends MergeStrategy {
  override lazy val name = "flink"

  override def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val targets = files.flatMap(candidate => {
      val (sourceJar, _, _, _) = sourceOfFileForMerge(tempDir, candidate)
      val jarMatch             = jar.findFirstIn(sourceJar.getName).isDefined
      val prefixMatch          = exclude.exists(prefix => candidate.getParent.startsWith(prefix))
      if (jarMatch && prefixMatch) None else Some(candidate -> path)
    })
    targets.headOption match {
      case Some(value) => Right(List(value))
      case None        => Left("all merge candidates were excluded")
    }
  }

  private val PathRE = "([^/]+)/(.*)".r

  // copied from AssemblyUtils, as it's marked private there
  def sourceOfFileForMerge(tempDir: File, f: File): (File, File, String, Boolean) = {
    val baseURI            = tempDir.getCanonicalFile.toURI
    val otherURI           = f.getCanonicalFile.toURI
    val relative           = baseURI.relativize(otherURI)
    val PathRE(head, tail) = relative.getPath
    val base               = tempDir / head

    if ((tempDir / (head + ".jarName")) exists) {
      val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
      (new File(jarName), base, tail, true)
    } else {
      val dirName = IO.read(tempDir / (head + ".dir"), IO.utf8)
      (new File(dirName), base, tail, false)
    } // if-else
  }
}
