package io.waylay.influxdb

import com.whisk.docker.{DockerReadyChecker, DockerContainer, DockerKit}

import scala.concurrent.duration._

trait DockerInfluxDBService extends DockerKit {

  val DefaultInfluxDBAdminPort = 8083
  val DefaultInfluxDBPort = 8086

  val influxdbContainer = DockerContainer("tutum/influxdb:0.10")
    .withPorts(
      DefaultInfluxDBPort -> None,
      DefaultInfluxDBAdminPort -> None
    )
    .withReadyChecker(
      DockerReadyChecker
        .HttpResponseCode(DefaultInfluxDBPort, "/ping", code = 204)
        .within(100.millis)
        .looped(20, 250.millis)
    )

  abstract override def dockerContainers: List[DockerContainer] = influxdbContainer :: super.dockerContainers
}

