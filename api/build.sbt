name := "api"

libraryDependencies ++= Seq(
//  "ml.dmlc" %% "xgboost4j" % "1.2.0" excludeAll (
//    ExclusionRule(organization = "com.esotericsoftware"),
//    ExclusionRule(organization = "com.typesafe.akka"),
//    ExclusionRule(organization = "org.scala-lang"),
//    ExclusionRule(organization = "org.scalatest")
//  )
) ++ Deps.httpsDeps
