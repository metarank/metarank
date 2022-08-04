package ai.metarank.mode

import ai.metarank.config.MPath.LocalPath
import ai.metarank.mode.BootstrapSavepointTest.{BootstrapFunction, Keyer}
import ai.metarank.mode.bootstrap.Bootstrap.{FeatureValueKeySelector, StateKeySelector}
import ai.metarank.model.ScopeType.ItemScope
import ai.metarank.util.FlinkTest
import better.files.File
import io.findify.featury.flink.FeatureJoinFunction.FeatureJoinBootstrapFunction
import io.findify.featury.flink.{FeatureBootstrapFunction, Featury}
import io.findify.featury.flink.format.{BulkCodec, BulkInputFormat}
import io.findify.featury.flink.util.Compress
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SString, ScalarState, Schema, State, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.flinkadt.api.deriveTypeInformation
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.state.api.functions.KeyedStateBootstrapFunction
import org.apache.flink.state.api.{OperatorTransformation, Savepoint, SavepointWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.flink.api._

import scala.language.higherKinds

class BootstrapSavepointTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "make savepoint" in {
    import io.findify.flinkadt.api._
    val path = LocalPath(File.newTemporaryDirectory(prefix = "savepoint_").deleteOnExit())
    val stateSource = env.fromElements[(String, Int)](
      "foo" -> 1
    )

    val transformStateless = OperatorTransformation
      .bootstrapWith(stateSource.javaStream)
      .keyBy(Keyer, implicitly[TypeInformation[String]])
      .transform(BootstrapFunction)

    SavepointWriter
      .newSavepoint(new EmbeddedRocksDBStateBackend(), 32)
      .withOperator("process-stateless-writes", transformStateless)
      .write(path.uri)

    env.execute("savepoint")

  }
}

object BootstrapSavepointTest {
  case object Keyer extends KeySelector[(String, Int), String] {
    override def getKey(value: (String, Int)): String = value._1
  }
  case object BootstrapFunction extends KeyedStateBootstrapFunction[String, (String, Int)] {
    override def processElement(
        value: (String, Int),
        ctx: KeyedStateBootstrapFunction[String, (String, Int)]#Context
    ): Unit = {}
  }
}
