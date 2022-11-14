package ai.metarank.main

import ai.metarank.main.CliArgs.AutoFeatureArgs
import ai.metarank.main.command.AutoFeature
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.main.command.autofeature.rules.RuleSet.RuleSetType.StableRuleSet
import ai.metarank.model.Event
import ai.metarank.util.RanklensEvents
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

import java.nio.file.Paths

class AutofeatureTest extends AnyFlatSpec with Matchers {
  it should "generate test config for ranklens" in {
    val args   = AutoFeatureArgs(Paths.get("/tmp"), Paths.get("/tmp"))
    val result = AutoFeature.run(Stream.emits(RanklensEvents()), RuleSet.stable(args)).unsafeRunSync()
    result.features.size shouldBe 8
  }
}
