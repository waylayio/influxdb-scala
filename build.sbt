import sbt.Keys.{crossScalaVersions, scalacOptions}

val playJsonVersion      = "3.0.6"
val playVersion          = "2.7.3" // test only
val playWsVersion        = "2.1.11"
val slf4jVersion         = "2.0.18"
val logbackVersion       = "1.6.2"
val specs2Version        = "4.23.0"
val dockerTestkitVersion = "0.12.0"

val scala2_12 = "2.12.21"
val scala2_13 = "2.13.18"

ThisBuild / versionScheme           := Some("semver-spec")
ThisBuild / dynverSonatypeSnapshots := true

ThisBuild / scalaVersion       := scala2_13
ThisBuild / crossScalaVersions := Seq(scala2_12, scala2_13)

ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-java8-compat"       % VersionScheme.Always,
  "org.scala-lang.modules" %% "scala-parser-combinators" % VersionScheme.Always
)

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

ThisBuild / organization := "io.waylay"
ThisBuild / homepage     := Some(uri("https://waylay.io"))
ThisBuild / developers   := List(
  Developer(
    "ramazanyich",
    "Ramil Israfilov",
    "ramazanyich@gmail.com",
    uri("https://github.com/ramazanyich")
  ),
  Developer("brunoballekens", "Bruno Ballekens", "bruno@waylay.io", uri("https://github.com/brunoballekens"))
)
ThisBuild / licenses := List("MIT License" -> uri("http://www.opensource.org/licenses/mit-license.php"))

lazy val repoSettings = Seq(
  publishTo := {
    val nexus = "https://nexus.waylay.io"
    if (isSnapshot.value)
      Some("Waylay snapshot repo" at nexus + "/repository/maven-snapshots")
    else
      Some("Waylay releases repo" at nexus + "/repository/maven-releases")
  }
)

lazy val testDependencies = Seq(
  "ch.qos.logback"     % "logback-classic"        % logbackVersion       % Test,
  "org.specs2"        %% "specs2-core"            % specs2Version        % Test,
  "org.specs2"        %% "specs2-junit"           % specs2Version        % Test,
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion        % Test,
  ("com.whisk"        %% "docker-testkit-core"    % dockerTestkitVersion % Test).excludeAll(nettyExclusions *)
).map(_.excludeAll(libraryExclusions *))

lazy val root = (project in file("."))
  .settings(
    name := "influxdb-scala",
    libraryDependencies ++= Seq(
      "org.playframework" %% "play-json"               % playJsonVersion,
      "com.typesafe.play" %% "play-ws-standalone"      % playWsVersion,
      "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion,
      "org.slf4j"          % "slf4j-api"               % slf4jVersion,
      "org.slf4j"          % "jcl-over-slf4j"          % slf4jVersion
    ).map(_.excludeAll(libraryExclusions *)) ++ testDependencies,
    releaseNotesURL := scmInfo.value.map(scm => uri(s"${scm.browseUrl}/releases"))
  )
  .settings(repoSettings)

lazy val integration = (project in file("integration"))
  .dependsOn(root)
  .settings(
    name                     := "influxdb-scala-integration-tests",
    publish / skip           := true,
    Test / fork              := true,
    Test / parallelExecution := false,
    Test / baseDirectory     := (LocalRootProject / baseDirectory).value,
    Test / scalaSource       := (LocalRootProject / baseDirectory).value / "src" / "it" / "scala",
    Test / resourceDirectory := (LocalRootProject / baseDirectory).value / "src" / "it" / "resources",
    libraryDependencies ++= testDependencies
  )

git.remoteRepo := "git@github.com:waylayio/influxdb-scala.git"
