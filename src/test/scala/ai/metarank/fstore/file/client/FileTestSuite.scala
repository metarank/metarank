package ai.metarank.fstore.file.client

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.Suite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait FileTestSuite extends AnyFlatSpec with Matchers { this: Suite =>
  def make(): FileClient

  it should "read-write" in {
    val db = make()
    db.put("foo".getBytes(), "bar".getBytes())
    val r = db.get("foo".getBytes()).map(b => new String(b))
    r shouldBe Some("bar")
    db.close()
  }

  it should "read-write-del-read" in {
    val db = make()
    db.put("foo".getBytes(), "bar".getBytes())
    db.del("foo".getBytes())
    val r = db.get("foo".getBytes()).map(b => new String(b))
    r shouldBe None
    db.close()
  }

  it should "iterate forward" in {
    val db = make()
    db.put("a1".getBytes(), "bar".getBytes())
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    db.put("z1".getBytes(), "bar".getBytes())
    val r = db.firstN("foo".getBytes(), 10).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo1", "foo2", "foo3")
    db.close()
  }

  it should "iterate forward for 1" in {
    val db = make()
    db.put("a1".getBytes(), "bar".getBytes())
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("z1".getBytes(), "bar".getBytes())
    val r = db.firstN("foo".getBytes(), 10).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo1")
    db.close()
  }

  it should "iterate backward" in {
    val db = make()
    db.put("a1".getBytes(), "bar".getBytes())
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    db.put("z1".getBytes(), "bar".getBytes())
    val r = db.lastN("foo".getBytes(), 2).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo3", "foo2")
    db.close()
  }

  it should "iterate for one" in {
    val db = make()
    db.put("a1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("z1".getBytes(), "bar".getBytes())
    val r = db.lastN("foo".getBytes(), 2).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo2")
    db.close()
  }

  it should "iterate backward from last" in {
    val db = make()
    db.put("a1".getBytes(), "bar".getBytes())
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    val r = db.lastN("foo".getBytes(), 2).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo3", "foo2")
    db.close()
  }

  it should "iterate backward till first" in {
    val db = make()
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    val r = db.lastN("foo".getBytes(), 3).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo3", "foo2", "foo1")
    db.close()
  }

  it should "iterate forward till last" in {
    val db = make()
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    val r = db.firstN("foo".getBytes(), 3).toList
    r.map(kv => new String(kv.key)) shouldBe List("foo1", "foo2", "foo3")
    db.close()
  }

  it should "read all with stream" in {
    val db = make()
    db.put("foo1".getBytes(), "bar".getBytes())
    db.put("foo2".getBytes(), "bar".getBytes())
    db.put("foo3".getBytes(), "bar".getBytes())
    val result = fs2.Stream.fromBlockingIterator[IO](db.firstN("foo".getBytes(), 3), 128).compile.toList.unsafeRunSync()
    result.map(kv => new String(kv.key)) shouldBe List("foo1", "foo2", "foo3")
    db.close()
  }

}
