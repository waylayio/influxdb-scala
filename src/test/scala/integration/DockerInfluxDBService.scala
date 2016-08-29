package integration

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.whisk.docker._
import de.gesellix.socketfactory.unix.UnixSocketFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait DockerInfluxDBService extends DockerKit {
  val DefaultInfluxDBAdminPort = 8083
  val DefaultInfluxDBPort = 8086

  val f = new UnixSocketFactory
  println(f.supports("unix"))

  println(getClass.getSimpleName + "!!!\n" + DefaultDockerClientConfig.createDefaultConfigBuilder().build())


  val influxdbContainer = DockerContainer("influxdb:1.0.0-rc1-alpine")
    .withPorts(
      DefaultInfluxDBPort -> None,
      DefaultInfluxDBAdminPort -> None
    )
    .withReadyChecker(
//      PrintingLogLineContains("Listening on HTTP: [::]:8086")
      DockerReadyChecker
        .HttpResponseCode(DefaultInfluxDBPort, "/ping", code = 204)
        .within(100.millis)
        .looped(20, 250.millis)
    )



  abstract override def dockerContainers: List[DockerContainer] = influxdbContainer :: super.dockerContainers
}

case class PrintingLogLineContains(str: String) extends DockerReadyChecker {
  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
    ec: ExecutionContext): Future[Boolean] = {
    for {
      id <- container.id
      _ <- docker.withLogStreamLines(id, withErr = true){line => println(line);line.contains(str)}
    } yield {
      true
    }
  }
}