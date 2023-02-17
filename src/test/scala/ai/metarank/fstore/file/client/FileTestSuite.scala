package ai.metarank.fstore.file.client

import ai.metarank.fstore.file.client.FileClient.PrefixSize
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.Suite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait FileTestSuite extends AnyFlatSpec with Matchers { this: Suite =>
  def makeHash(): HashDB
  def makeTree(): SortedDB[String]

  "hash" should "read-write" in {
    val db = makeHash()
    db.put("foo", "bar".getBytes())
    val r = db.get("foo").map(b => new String(b))
    r shouldBe Some("bar")
  }

  it should "read-write-del-read" in {
    val db = makeHash()
    db.put("foo", "bar".getBytes())
    db.del("foo")
    val r = db.get("foo").map(b => new String(b))
    r shouldBe None
  }

  it should "read all" in {
    val db = makeHash()
    db.put("foo1", "bar".getBytes())
    db.put("foo2", "bar".getBytes())
    db.put("foo3", "bar".getBytes())
    val result = fs2.Stream.fromBlockingIterator[IO](db.all(), 128).compile.toList.unsafeRunSync()
    result.map(kv => new String(kv._1)) shouldBe List("foo3", "foo1", "foo2")
  }

  "tree" should "read-write" in {
    val db = makeTree()
    db.put("foo", "bar")
    val r = db.get("foo")
    r shouldBe Some("bar")
  }

  it should "read-write-del-read" in {
    val db = makeTree()
    db.put("foo", "bar")
    db.del("foo")
    val r = db.get("foo")
    r shouldBe None
  }

  it should "read all" in {
    val db = makeTree()
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val result = fs2.Stream.fromBlockingIterator[IO](db.all(), 128).compile.toList.unsafeRunSync()
    result.map(_._1) shouldBe List("foo1", "foo2", "foo3")
  }
  "tree" should "iterate forward" in {
    val db = makeTree()
    db.put("a1", "bar")
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    db.put("z1", "bar")
    val r = db.firstN("foo", 10).toList
    r.map(_._1) shouldBe List("foo1", "foo2", "foo3")
  }

  it should "iterate forward for 1" in {
    val db = makeTree()
    db.put("a1", "bar")
    db.put("foo1", "bar")
    db.put("z1", "bar")
    val r = db.firstN("foo", 10).toList
    r.map(_._1) shouldBe List("foo1")
  }

  it should "iterate backward" in {
    val db = makeTree()
    db.put("a1", "bar")
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    db.put("z1", "bar")
    val r = db.lastN("foo", 2).toList
    r.map(_._1) shouldBe List("foo3", "foo2")
  }

  it should "iterate for one" in {
    val db = makeTree()
    db.put("a1", "bar")
    db.put("foo2", "bar")
    db.put("z1", "bar")
    val r = db.lastN("foo", 2).toList
    r.map(_._1) shouldBe List("foo2")
  }

  it should "iterate backward from last" in {
    val db = makeTree()
    db.put("a1", "bar")
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val r = db.lastN("foo", 2).toList
    r.map(_._1) shouldBe List("foo3", "foo2")
  }

  it should "iterate backward till first" in {
    val db = makeTree()
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val r = db.lastN("foo", 3).toList
    r.map(_._1) shouldBe List("foo3", "foo2", "foo1")
  }

  it should "iterate forward till last" in {
    val db = makeTree()
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val r = db.firstN("foo", 3).toList
    r.map(_._1) shouldBe List("foo1", "foo2", "foo3")
  }

  it should "read all with stream" in {
    val db = makeTree()
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val result = fs2.Stream.fromBlockingIterator[IO](db.firstN("foo", 3), 128).compile.toList.unsafeRunSync()
    result.map(_._1) shouldBe List("foo1", "foo2", "foo3")
  }

  it should "measure size" in {
    val db = makeTree()
    db.put("foo1", "bar")
    db.put("foo2", "bar")
    db.put("foo3", "bar")
    val size = db.size()
    size shouldBe PrefixSize(12, 9, 3)
  }

}
