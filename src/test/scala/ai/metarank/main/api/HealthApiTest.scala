package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Schema
import ai.metarank.util.TestConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HealthApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = FeatureMapping.fromFeatureSchema(TestConfig().features, TestConfig().models)
  lazy val api     = HealthApi(MemPersistence(mapping.schema))
}
