# influxdb-scala
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.waylay.influxdb/influxdb-scala_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.waylay.influxdb/influxdb-scala_2.11)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2e19cc02e06f4ed4913d7902d719b6e7)](https://www.codacy.com/app/francisdb/influxdb-scala?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=waylayio/influxdb-scala&amp;utm_campaign=Badge_Grade)
Scala InfluxDB driver

This is our own implementation that depends on `play-ws` and `play-json`. Not to be confused with the abandoned offical influxdb-scala driver.

## Usage

Add this to your build.sbt

```scala
libraryDependencies ++= Seq(
  // other dependencies here
  "io.waylay.influxdb" %% "influxdb-scala" % "3.1.3"
)
```

Some example usage code is available [in the tests](src/it/scala/io/waylay/influxdb/InfluxDBSpec.scala)

snapshots are available at: `https://oss.sonatype.org/content/repositories/snapshots`

### Releases/versioning

[`sbt-release`](https://github.com/sbt/sbt-release) is used for releases. Use `sbt release`.

After release is finished the uploaded artifacts are not available automatically on OSS Sonatype main repositories.
They are published to the staging repositories.
Follow guidelines provided by Sonatype at [Releasing](https://central.sonatype.org/publish/release/#releasing-deployment-from-ossrh-to-the-central-repository-introduction) page
to make the artifacts available publicly.

### ScalaDoc

Published [here](https://waylayio.github.io/influxdb-scala/latest/api) on [Github Pages](https://pages.github.com/) with [sbt-site](https://github.com/sbt/sbt-site) and [sbt-ghpages](https://github.com/sbt/sbt-ghpages). Automatically published as part of release process.

Manually publishing the ScalaDocs can be done with the following command:

```
$ sbt ghpagesPushSite
```

