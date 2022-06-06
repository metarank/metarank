package ai.metarank.mode

import ai.metarank.config.EventSourceConfig.FileSourceConfig
import ai.metarank.config.MPath.LocalPath
import ai.metarank.source.FileEventSource
import better.files.File
import org.apache.flink.core.fs.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FileEventSourceTest extends AnyFlatSpec with Matchers {
  val dir     = File.newTemporaryDirectory()
  val child   = dir.createChild("child", asDirectory = true)
  val leaf    = child.createChild("events.json.gz")
  val exclude = child.createChild("conf.yml")
  val source  = new FileEventSource(FileSourceConfig(LocalPath(dir.toString())))

  "file filter" should "accept dirs" in {
    source.selectFile(new Path("file://" + dir.toString())) shouldBe true
  }

  it should "accept nested dirs" in {
    source.selectFile(new Path("file://" + child.toString())) shouldBe true
  }

  it should "accept gzipped files" in {
    source.selectFile(new Path("file://" + leaf.toString())) shouldBe true
  }

  it should "reject yamls" in {
    source.selectFile(new Path("file://" + exclude.toString())) shouldBe false
  }
}
