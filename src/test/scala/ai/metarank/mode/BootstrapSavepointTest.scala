package ai.metarank.mode

import ai.metarank.mode.BootstrapSavepointTest.{BootstrapFunction, Keyer}
import ai.metarank.mode.bootstrap.Bootstrap.{FeatureValueKeySelector, StateKeySelector}
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.util.FlinkTest
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
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.state.api.functions.KeyedStateBootstrapFunction
import org.apache.flink.state.api.{OperatorTransformation, Savepoint}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.language.higherKinds

class BootstrapSavepointTest extends AnyFlatSpec with Matchers {
  it should "make savepoint" in {
    import io.findify.flinkadt.api._
    import org.apache.flink.DataSetOps._

    val batch = ExecutionEnvironment.getExecutionEnvironment
    batch.setParallelism(1)
    val stateSource = batch.fromElements[(String, Int)](
      "foo" -> 1
    )

    val transformStateless = OperatorTransformation
      .bootstrapWith(stateSource.toJava)
      .keyBy(Keyer, implicitly[TypeInformation[String]])
      .transform(BootstrapFunction)

    Savepoint
      .create(new EmbeddedRocksDBStateBackend(), 32)
      .withOperator("process-stateless-writes", transformStateless)
      .write(s"file:///tmp/savepoint")

    batch.execute("savepoint")

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
