package ai.metarank.util.fs

import ai.metarank.config.MPath.LocalPath
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class LocalFSTest extends AnyFlatSpec with Matchers {
  it should "read files" in {
    val file   = File.createTempFile("read-test-", ".tmp")
    val stream = new FileOutputStream(file)
    IOUtils.write("test", stream, StandardCharsets.UTF_8)
    stream.close()
    val read = FS.read(LocalPath(file.toString), Map.empty).unsafeRunSync()
    new String(read) shouldBe "test"
  }

  it should "write files" in {
    val file = File.createTempFile("write-test-", ".tmp")
    FS.write(LocalPath(file.toString), "test".getBytes(StandardCharsets.UTF_8), Map.empty).unsafeRunSync()
    val read = FS.read(LocalPath(file.toString), Map.empty).unsafeRunSync()
    new String(read) shouldBe "test"
  }

  it should "list files" in {
    val root     = Files.createTempDirectory("test-dir")
    val child1   = Files.createTempFile(root, "file-", "")
    val child2   = Files.createTempFile(root, "file-", "")
    val rootList = LocalFS().listRecursive(LocalPath(root.toString)).unsafeRunSync()
    rootList.size shouldBe 2
  }
}
