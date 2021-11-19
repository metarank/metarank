package ai.metarank.config

import ai.metarank.config.Config.{ApiConfig, SchemaConfig, StoreConfig}
import ai.metarank.config.IngestConfig.FileIngestConfig
import ai.metarank.config.ValueStoreConfig.RedisStoreConfig
import ai.metarank.model.FeatureSchema.NumberFeatureSchema
import ai.metarank.model.FeatureSource.Item
import ai.metarank.model.FieldSchema.{NumberFieldSchema, StringFieldSchema}
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse example config" in {
    val yaml =
      """api:
        |  port: 8080
        |schema:
        |  metadata:
        |    - name: price
        |      type: number
        |      required: true
        |  impression:
        |    - name: query
        |      type: string
        |  interaction:
        |    - name: type 
        |      type: string
        |feature:
        |  - name: price
        |    type: number
        |    field: price
        |    source: item
        |ingest:
        |  type: file
        |  path: file:///foo/bar
        |store:
        |  type: redis
        |  host: localhost
        |  port: 6379""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        api = ApiConfig(8080),
        schema = SchemaConfig(
          metadata = List(NumberFieldSchema("price", true)),
          impression = List(StringFieldSchema("query")),
          interaction = List(StringFieldSchema("type"))
        ),
        feature = List(NumberFeatureSchema("price", "price", Item)),
        ingest = FileIngestConfig("file:///foo/bar"),
        store = RedisStoreConfig("localhost", 6379)
      )
    )
  }
}
