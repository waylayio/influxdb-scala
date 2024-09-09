import sbt.Keys.{crossScalaVersions, scalacOptions}

val playJsonVersion      = "3.0.4"
val playVersion          = "2.7.3" // test only
val playWsVersion        = "2.1.11"
val slf4jVersion         = "2.0.16"
val logbackVersion       = "1.5.8"
val specs2Version        = "4.20.8"
val dockerTestkitVersion = "0.12.0"

val scala2_12 = "2.12.20"
val scala2_13 = "2.13.14"

scalaVersion       := scala2_13
crossScalaVersions := Seq(scala2_12, scala2_13)

releaseCrossBuild := true

ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")

// we need both Test and IntegrationTest scopes for a correct pom, see https://github.com/sbt/sbt/issues/1380
val TestAndIntegrationTest = IntegrationTest.name + "," + Test.name

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
  "netty-codec-http"
).map(name => ExclusionRule(organization = "io.netty", name = name))

ThisBuild / organization := "io.waylay.influxdb"

lazy val root = (project in file("."))
  .settings(
    name := "influxdb-scala",
    libraryDependencies ++= Seq(
      "org.playframework" %% "play-json"               % playJsonVersion,
      "com.typesafe.play" %% "play-ws-standalone"      % playWsVersion,
      "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion,
      // "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.slf4j" % "slf4j-api"      % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      // TEST
      "ch.qos.logback" % "logback-classic" % logbackVersion % TestAndIntegrationTest,
      "org.specs2"    %% "specs2-core"     % specs2Version  % TestAndIntegrationTest,
      "org.specs2"    %% "specs2-junit"    % specs2Version  % TestAndIntegrationTest,
      // "com.typesafe.play" %% "play-ahc-ws" % playVersion % TestAndIntegrationTest, // neede for play-mockws
      "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion % TestAndIntegrationTest,
      "com.whisk" %% "docker-testkit-core" % dockerTestkitVersion % TestAndIntegrationTest excludeAll (nettyExclusions: _*)
    ).map(_.excludeAll(libraryExclusions: _*))
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

val publishScalaDoc = (ref: ProjectRef) =>
  ReleaseStep(
    action = releaseStepTaskAggregated(ref / ghpagesPushSite) // publish scaladoc
  )

val runIntegrationTest = (ref: ProjectRef) =>
  ReleaseStep(
    action = releaseStepTaskAggregated(ref / IntegrationTest / test)
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
