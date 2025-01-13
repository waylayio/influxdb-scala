package io.waylay.influxdb

import io.waylay.influxdb.Influx._
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.Json.{arr, obj}
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.JsonBodyReadables._

import java.time.Instant
import scala.concurrent.duration.{MILLISECONDS, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class InfluxDB2(
    ws: StandaloneWSClient,
    host: String = "localhost",
    org: String,
    token: String,
    port: Int = InfluxDB.DEFAULT_PORT,
    schema: String = "http",
    defaultRetention: String = "INF"
)(implicit ec: ExecutionContext) {
  import InfluxDB._

  private final val logger  = LoggerFactory.getLogger(getClass)
  private final val baseUrl = s"$schema://$host:$port"

  def ping: Future[Version] = {
    val req = ws
      .url(baseUrl + "/ping")
      .withRequestTimeout(INFLUX_PING_REQUEST_TIMEOUT)
    logger.debug(s" -> $req")
    req.get().map { response =>
      logger.debug("status: " + response.status)
      val body    = Some(response.body).filter(_.nonEmpty).getOrElse("[empty]")
      val version = response.header("X-Influxdb-Version").get
      logger.info(s"influxdb ping completed, version = $version, body = $body")
      response.header("X-Influxdb-Version").get
    }
  }

  def ready: Future[JsObject] =
    authenticatedUrl(s"${baseUrl}/ready").get().flatMap { response =>
      logger.debug("status: " + response.status)
      response.status match {
        case 200 => // ok
          logger.trace(s"got data\n${Json.prettyPrint(response.body[JsValue])}")
          Future.successful(response.body[JsValue].as[JsObject])
        case other =>
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
      }
    }

  def storeAndMakeBucketIfNeeded(
      bucketName: String,
      points: Seq[IPoint],
      createDbIfNeeded: Boolean = true,
      precision: TimeUnit = MILLISECONDS
  ): Future[Unit] = {
    val data = WriteProtocol.write(precision, points: _*)
    logger.debug(s"storing data to $bucketName\n$data")
    val req = authenticatedUrlForBucket(bucketName, Write2, precision)
    req.post(data).flatMap { response =>
      logger.debug(response.toString)
      logger.debug(response.body)
      response.status match {
        case 404 if createDbIfNeeded =>
          // make sure we don't end up in an endless loop
          createBucket(bucketName)
            .flatMap(_ => storeAndMakeBucketIfNeeded(bucketName, points, createDbIfNeeded = false))
        case 204 => // ok
          logger.info(s"stored ${points.length} points to $bucketName")
          Future.successful(())
        case _ =>
          Future.failed(
            new RuntimeException(
              s"""Got status ${response.status} with body: ${response.body.stripLineEnd} when saving ${points.length} points to $bucketName """
            )
          )
      }
    }
  }
  def createBucket(bucketName: String): Future[Unit] = {

    val duration = durationLiteralToDuration(defaultRetention)
    if (duration == -1) {
      Future.failed(new RuntimeException(s"Invalid retention duration: $defaultRetention"))
    } else {
      getOrgId(org).flatMap { orgId =>
        val body = obj(
          "orgID"          -> s"$orgId",
          "name"           -> bucketName,
          "retentionRules" -> arr(obj("type" -> "expire", "everySeconds" -> duration))
        )
        authenticatedUrlFor(Bucket).addHttpHeaders("Content-Type" -> "application/json").post(body).flatMap {
          response =>
            logger.info("status: " + response.status)
            logger.debug(response.headers.mkString("\n"))
            logger.debug(response.body)

            if (response.status != 201) {
              Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
            } else {
              Future.successful(())
            }
        }
      }
    }
  }

  def deleteSeries(bucketName: String, predicate: String, startTime: Instant, stopTime: Instant): Future[Unit] = {
    val data = obj("predicate" -> predicate, "start" -> startTime, "stop" -> stopTime)
    authenticatedUrlForBucket(bucketName, Delete2).post(data).flatMap { response =>
      logger.debug("status: " + response.status)
      response.status match {

        case 204 => // ok
          Future.successful(())

        case other =>
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
      }
    }

  }
  def query(
      bucketName: String,
      query: String,
      chunkSize: Option[Int] = None,
      epoch: Option[Epoch] = None
  ): Future[Results] = {
    logger.debug(query)

    val extraQueryString = Seq(
      chunkSize.map("chunk_size" -> _.toString),
      epoch.map("epoch" -> epochToQueryParam(_))
    ).flatten

    val req = authenticatedUrlForDatabase(bucketName, Query)
      .addQueryStringParameters("q" -> query)
      .addQueryStringParameters(extraQueryString: _*)

    logger.debug(s" -> $req")
    req.get().flatMap { response =>
      logger.debug("status: " + response.status)
      response.status match {
        case 404 =>
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
        case 200 => // ok
          logger.debug(s"got data\n${Json.prettyPrint(response.body[JsValue])}")
          import io.waylay.influxdb.query.QueryResultProtocol._
          val results = response.body[JsValue].as[Results]
          if (results.hasDatabaseNotFoundError) {
            Future.successful(Results(Some(Seq.empty), None))
          } else {
            Future.successful(results)
          }
        case other =>
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
      }
    }
  }

  def getOrgId(name: String): Future[String] =
    authenticatedUrl(s"$baseUrl/api/v2/orgs").addQueryStringParameters("org" -> name).get().flatMap { resp =>
      if (resp.status != 200) {
        Future.failed(new RuntimeException(s"Got status ${resp.status} with body: ${resp.body}"))
      } else {
        (resp.body[JsValue] \ "orgs")
          .as[Seq[JsObject]]
          .headOption
          .map(o => (o \ "id").as[String])
          .map(Future.successful)
          .getOrElse(Future.failed(new RuntimeException("organisation id not found")))

      }
    }

  def getRetention(bucketName: String): Future[Long] =
    authenticatedUrlFor(Bucket).addQueryStringParameters("name" -> bucketName).get().flatMap { resp =>
      if (resp.status != 200) {
        Future.failed(new RuntimeException(s"Got status ${resp.status} with body: ${resp.body}"))
      } else {
        (resp.body[JsValue] \ "buckets" \ 0 \ "retentionRules")
          .as[Seq[JsObject]]
          .headOption
          .map(o => (o \ "everySeconds").as[Long])
          .map(Future.successful)
          .getOrElse(Future.failed(new RuntimeException(s"bucket ${bucketName} not found")))
      }
    }

  private def durationLiteralToDuration(durationLiteral: String): Long =
    durationLiteral.toLowerCase match {
      case "inf" => 0
      case s     => Try(parseDurationLiteral(durationLiteral).toMillis / 1000).getOrElse(-1)
    }

  private def authenticatedUrlForDatabase(bucketName: String, method: Method) = {

    val url = s"$baseUrl/${method.endpoint}"
    authenticatedUrl(url).addQueryStringParameters(
      "db" -> bucketName
    )
  }

  private def authenticatedUrlForBucket(
      bucketName: String,
      method: Method,
      precision: TimeUnit = MILLISECONDS
  ) = {
    val influxPrecision = precision match {
      case MILLISECONDS => "ms"
      case _            => throw new RuntimeException(s"precision $precision not implemented")
    }

    val url = s"$baseUrl/${method.endpoint}"
    authenticatedUrl(url).addQueryStringParameters(
      "bucket"    -> bucketName,
      "org"       -> org,
      "precision" -> influxPrecision
    )
  }
  private def authenticatedUrlFor(method: Method) = {
    val url = s"$baseUrl/${method.endpoint}"
    authenticatedUrl(url)
  }
  private def authenticatedUrl(url: String) =
    ws.url(url).withRequestTimeout(INFLUX_REQUEST_TIMEOUT).withHttpHeaders("Authorization" -> s"Token $token")
}
