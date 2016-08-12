import ReleaseTransformations._
import GhPagesKeys._

val playVersion = "2.5.4"
val slf4jVersion = "1.7.12"
val logbackVersion = "1.1.7"
val specs2Version = "3.7.3"

lazy val libraryExclusions = Seq(
  ExclusionRule("org.slf4j", "slf4j-log4j12"),
  ExclusionRule("commons-logging", "commons-logging"),
  ExclusionRule("org.apache.logging.log4j", "log4j-core")
)

lazy val nettyExclusions = Seq("netty-codec", "netty-handler-proxy", "netty-handler", "netty-transport-native-epoll",
  "netty-codec-socks", "netty-codec-http").map(name => ExclusionRule(organization = "io.netty", name = name))

organization in ThisBuild := "io.waylay.influxdb"

lazy val root = (project in file("."))
  .settings(
    name := "influxdb-scala",
    scalaVersion := "2.11.8",

    // Be wary of adding extra dependencies (especially the Waylay common dependencies)
    // They may pull in a newer Netty version, breaking play-ws
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playVersion,
      "com.typesafe.play" %% "play-ws" % playVersion, // pulls in the whole of play
      //"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,

      // TEST
      "ch.qos.logback" % "logback-classic" % logbackVersion % Test,
      "org.specs2" %% "specs2-core" % specs2Version % Test,
      "org.specs2" %% "specs2-junit" % specs2Version % Test,
      "com.whisk" %% "docker-testkit-specs2" % "0.9.0-M5" % Test excludeAll(nettyExclusions:_*),

      // INTEGRATION TESTS
      // TODO investigate if we can do this with specs2
      "org.scalatest" %% "scalatest" % "2.2.6" % Test,
      "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-M5" % Test excludeAll(nettyExclusions:_*),
      "com.jsuereth" %% "scala-arm" % "1.4" % Test
    ).map(_.excludeAll(libraryExclusions:_*))
  )


ghpages.settings
enablePlugins(SiteScaladocPlugin)

val publishScalaDoc = (ref: ProjectRef) => ReleaseStep(
  action = releaseStepTaskAggregated(GhPagesKeys.pushSite in ref) // publish scaladoc
)

val runIntegrationTest = (ref: ProjectRef) => ReleaseStep(
  action = releaseStepTaskAggregated(test in IntegrationTest in ref)
)

releaseProcess <<= thisProjectRef apply { ref =>
  import sbtrelease.ReleaseStateTransformations._

  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    runIntegrationTest(ref),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    publishScalaDoc(ref),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}

git.remoteRepo := "git@github.com:waylayio/influxdb-scala.git"
