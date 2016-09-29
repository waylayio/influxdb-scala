# influxdb-scala
[![Build Status](https://travis-ci.org/waylayio/influxdb-scala.svg?branch=master)](https://travis-ci.org/waylayio/influxdb-scala)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.waylay.influxdb/influxdb-scala/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.waylay.influxdb/influxdb-scala)

Scala InfluxDB driver

This is our own implementation that depends on `play-ws` and `play-json`. Not to be confused with the abandonned offical influxdb-scala driver.

## Usage

Add this to your build.sbt

```scala
libraryDependencies ++= Seq(
  // other dependencies here
  "io.waylay.influxdb" %% "influxdb-scala" % "1.0.4"
)
```

snapshots are available at: `https://oss.sonatype.org/content/repositories/snapshots`
