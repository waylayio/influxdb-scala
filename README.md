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
  "io.waylay.influxdb" %% "influxdb-scala" % "4.0.0"
)
```

Some example usage code is available [in the tests](src/it/scala/io/waylay/influxdb/InfluxDBSpec.scala)

## Cutting a release

We use sbt-dynver in combination with github actions.

All you have to do is create a tag named v#.#.# Snapshots with git hash are also published automatically from master or branches
