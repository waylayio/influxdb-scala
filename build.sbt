import sbt.Keys.{crossScalaVersions, scalacOptions}

val playJsonVersion = "2.7.2"
val playVersion = "2.7.1"
val playAhcWsVersion = "2.0.3"
val slf4jVersion = "1.7.12"
val logbackVersion = "1.1.7"
val specs2Version = "3.9.2"
val dockerTestkitVersion = "0.9.8"

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.8"

scalaVersion := scala2_12
crossScalaVersions := Seq(scala2_11, scala2_12)
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

releaseCrossBuild := true

lazy val libraryExclusions = Seq(
  ExclusionRule("org.slf4j", "slf4j-log4j12"),
  ExclusionRule("commons-logging", "commons-logging"),
  ExclusionRule("org.apache.logging.log4j", "log4j-core")
)

// we need to stay compatible with play-ws
lazy val nettyExclusions = Seq(
  "netty-codec",
  "netty-handler-proxy",
  "netty-handler",
  "netty-transport-native-epoll",
  "netty-codec-socks",
  "netty-codec-http").map(name => ExclusionRule(organization = "io.netty", name = name))

organization in ThisBuild := "io.waylay.influxdb"

lazy val root = (project in file("."))
  .settings(
    name := "influxdb-scala",


    // Be wary of adding extra dependencies (especially the Waylay common dependencies)
    // They may pull in a newer Netty version, breaking play-ws
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.typesafe.play" %% "play-ws" % playVersion, // pulls in the whole of play
      //"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,

      // TEST
      "ch.qos.logback" % "logback-classic" % logbackVersion % Test,
      "org.specs2" %% "specs2-core" % specs2Version % Test,
      "org.specs2" %% "specs2-junit" % specs2Version % Test,
      "com.whisk" %% "docker-testkit-specs2" % dockerTestkitVersion % Test excludeAll(nettyExclusions:_*),
      "com.whisk" %% "docker-testkit-impl-spotify" % dockerTestkitVersion % Test,

      // INTEGRATION TESTS
      // TODO investigate if we can do this with specs2
      "com.typesafe.play" %% "play-ahc-ws-standalone" % playAhcWsVersion % Test,
      "com.typesafe.play" %% "play-ahc-ws" % playVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "com.whisk" %% "docker-testkit-scalatest" % dockerTestkitVersion % Test excludeAll(nettyExclusions:_*)
    ).map(_.excludeAll(libraryExclusions:_*))
  )

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

val publishScalaDoc = (ref: ProjectRef) => ReleaseStep(
  action = releaseStepTaskAggregated(ghpagesPushSite in ref) // publish scaladoc
)

val runIntegrationTest = (ref: ProjectRef) => ReleaseStep(
  action = releaseStepTaskAggregated(test in IntegrationTest in ref)
)

releaseProcess := {
  import sbtrelease.ReleaseStateTransformations._

  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    runIntegrationTest(thisProjectRef.value),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    publishScalaDoc(thisProjectRef.value),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}

git.remoteRepo := "git@github.com:waylayio/influxdb-scala.git"
