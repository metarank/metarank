import Deps._

name := "ingest"

libraryDependencies ++= Deps.httpsDeps

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % fs2Version
)
