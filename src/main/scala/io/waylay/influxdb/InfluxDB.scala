package io.waylay.influxdb

import io.waylay.influxdb.Influx._
import io.waylay.influxdb.query.QueryResultProtocol
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse, WSAuthScheme}
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.JsonBodyReadables._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object InfluxDB {

  final val DEFAULT_PORT = 8086

  final val INFLUX_REQUEST_TIMEOUT      = 1.minutes
  final val INFLUX_PING_REQUEST_TIMEOUT = 10.seconds

  /**
   * If you want timestamps in Unix epoch format include in your request the query string parameter epoch
   * where epoch=[h,m,s,ms,u,ns]
   *
   * See https://influxdb.com/docs/v0.9/guides/querying_data.html
   */
  sealed trait Epoch
  case object Hours        extends Epoch
  case object Minutes      extends Epoch
  case object Seconds      extends Epoch
  case object MilliSeconds extends Epoch
  case object MicroSeconds extends Epoch
  case object NanoSeconds  extends Epoch

  // not sure this is the best design
  // we could add automatic conversion from scala.concurrent.duration (no weeks/days there)
  // we could add implicits for something like 4.days (might conflict with the scala.concurrent.duration ones)
  sealed trait DurationUnit
  object DurationUnit {
    case object MicroSecond extends DurationUnit
    case object MilliSecond extends DurationUnit
    case object Second      extends DurationUnit
    case object Minute      extends DurationUnit
    case object Hour        extends DurationUnit
    case object Day         extends DurationUnit
    case object Week        extends DurationUnit

    def toMillis(durationUnit: DurationUnit): Long = durationUnit match {
      case Week        => 1000 * 60 * 60 * 24 * 7
      case Day         => 1000 * 60 * 60 * 24
      case Hour        => 1000 * 60 * 60
      case Minute      => 1000 * 60
      case Second      => 1000
      case MilliSecond => 1
      case MicroSecond => 1 // TODO do we want to work with microseconds?
    }
  }
  object Duration {
    import DurationUnit._
    def days(amount: Int): Duration         = Duration(amount, Day)
    def weeks(amount: Int): Duration        = Duration(amount, Week)
    def hours(amount: Int): Duration        = Duration(amount, Hour)
    def minutes(amount: Int): Duration      = Duration(amount, Minute)
    def seconds(amount: Int): Duration      = Duration(amount, Second)
    def milliseconds(amount: Int): Duration = Duration(amount, MilliSecond)
    def microseconds(amount: Int): Duration = Duration(amount, MicroSecond)
  }
  case class Duration(amount: Int, unit: DurationUnit) {
    def toMillis: Long = DurationUnit.toMillis(unit) * amount
  }

  // TODO we should probably make this more open to allow custom functions
  sealed trait IFunction

  object Count {
    def apply(field_key: String)  = new Count(Left(field_key))
    def apply(distinct: Distinct) = new Count(Right(distinct))

    // def unapply(distinct: Count) = distinct.either
  }
  case class Count(either: Either[String, Distinct])                                  extends IFunction
  case class Min(field_key: String)                                                   extends IFunction
  case class Max(field_key: String)                                                   extends IFunction
  case class Mean(field_key: String)                                                  extends IFunction
  case class Median(field_key: String)                                                extends IFunction
  case class Distinct(field_key: String)                                              extends IFunction
  case class Percentile(field_key: String, n: Double)                                 extends IFunction // or Int
  case class Derivative(field_key: Either[String, IFunction], rate: Option[Duration]) extends IFunction
  case class Sum(field_key: String)                                                   extends IFunction
  case class Stddev(field_key: String)                                                extends IFunction
  case class First(field_key: String)                                                 extends IFunction
  case class Last(field_key: String)                                                  extends IFunction

  sealed trait IFieldFilterOperation
  case object EQ  extends IFieldFilterOperation
  case object NE  extends IFieldFilterOperation
  case object LT  extends IFieldFilterOperation
  case object LTE extends IFieldFilterOperation
  case object GT  extends IFieldFilterOperation
  case object GTE extends IFieldFilterOperation

  sealed trait IFilter
  case class IFieldFilter(field_key: String, operator: IFieldFilterOperation, value: IFieldValue) extends IFilter
  case class AND(filter1: IFilter, filter2: IFilter, other: IFilter*)                             extends IFilter
  case class OR(filter1: IFilter, filter2: IFilter, other: IFilter*)                              extends IFilter
  case class NOT(filter: IFilter)                                                                 extends IFilter

  private def epochToQueryParam(epoch: Epoch) = epoch match {
    case Hours        => "h"
    case Minutes      => "m"
    case Seconds      => "s"
    case MilliSeconds => "ms"
    case MicroSeconds => "u"
    case NanoSeconds  => "ns"
  }

  private final val weeks        = """(\d+)w""".r
  private final val days         = """(\d+)d""".r
  private final val hours        = """(\d+)h""".r
  private final val minutes      = """(\d+)m""".r
  private final val seconds      = """(\d+)s""".r
  private final val milliseconds = """(\d+)ms""".r
  private final val microseconds = """(\d+)[u,µ]""".r

  /**
   * duration_lit        = int_lit duration_unit .
   * duration_unit       = "u" | "µ" | "s" | "h" | "d" | "w" | "ms" .
   */
  val parseDurationLiteral: PartialFunction[String, Duration] = {
    case weeks(count, _*)        => Duration.weeks(count.toInt)
    case days(count, _*)         => Duration.days(count.toInt)
    case hours(count, _*)        => Duration.hours(count.toInt)
    case minutes(count, _*)      => Duration.minutes(count.toInt)
    case seconds(count, _*)      => Duration.seconds(count.toInt)
    case milliseconds(count, _*) => Duration.milliseconds(count.toInt)
    case microseconds(count, _*) => Duration.microseconds(count.toInt)
  }

  private[influxdb] def durationLiteral(duration: Duration) = {
    import DurationUnit._
    val stringUnit = duration.unit match {
      case Week        => "w"
      case Day         => "d"
      case Hour        => "h"
      case Minute      => "m"
      case Second      => "s"
      case MilliSecond => "ms"
      case MicroSecond => "u" // or µ
    }
    duration.amount.toString + stringUnit
  }

  private sealed trait Method {
    def endpoint: String
  }
  private case object Write extends Method {
    override val endpoint = "write"
  }
  private case object Query extends Method {
    override val endpoint = "query"
  }
  private case object Ping extends Method {
    override val endpoint = "ping"
  }
}

class InfluxDB(
  ws: StandaloneWSClient,
  host: String = "localhost",
  port: Int = InfluxDB.DEFAULT_PORT,
  username: String = "root",
  password: String = "root",
  // var database: String = "",
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
      // make sure we consume the body, could be source of recent blocked influx
      val body    = Some(response.body).filter(_.nonEmpty).getOrElse("[empty]")
      val version = response.header("X-Influxdb-Version").get
      logger.info(s"influxdb ping completed, version = $version, body = $body")
      response.header("X-Influxdb-Version").get
    }
  }

  def getRetention(dbName: String): Future[Results] =
    authenticatedUrlFor(Query)
      .addQueryStringParameters("q" -> s"""SHOW RETENTION POLICIES ON "$dbName"""")
      .get()
      .flatMap(getResultsFromResponse)

  def stats: Future[Results] =
    authenticatedUrlFor(Query)
      .addQueryStringParameters("q" -> "SHOW STATS")
      .get()
      .flatMap(getResultsFromResponse)

  def diagnostics: Future[Results] =
    authenticatedUrlFor(Query)
      .addQueryStringParameters("q" -> "SHOW DIAGNOSTICS")
      .get()
      .flatMap(getResultsFromResponse)

  def query(
    databaseName: String,
    query: String,
    chunkSize: Option[Int] = None,
    epoch: Option[Epoch] = None
  ): Future[Results] = {
    logger.debug(query)

    val extraQueryString = Seq(
      chunkSize.map("chunk_size" -> _.toString),
      epoch.map("epoch" -> epochToQueryParam(_))
    ).flatten

    val req = authenticatedUrlForDatabase(databaseName, Query)
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
          import QueryResultProtocol._
          val results = response.body[JsValue].as[Results]
          if (results.hasDatabaseNotFoundError) {
            Future.successful(Results(Some(Seq.empty), None))
          } else if (results.hasErrors) {
            // possible errors:
            // too many points in the group by interval. maybe you forgot to specify a where time clause?
            // trying to do an aggregate on string values
            //   Unsupported count iterator type: *influxql.stringReduceSliceIterator
            Future.failed(new RuntimeException(results.allErrors.mkString(" | ")))
          } else {
            Future.successful(results)
          }
        case other =>
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
      }
    }
  }

  // TODO make precision it's own class since we have a limited amount of cases
  // default to nanoseconds like influx does
  // reuse for both query and write
  // precision=[n,u,ms,s,m,h] - sets the precision of the supplied Unix time values. If not present timestamps are assumed to be in nanoseconds
  // https://docs.influxdata.com/influxdb/v0.9/write_protocols/write_syntax/
  //
  def storeAndMakeDbIfNeeded(
    databaseName: String,
    points: Seq[IPoint],
    createDbIfNeeded: Boolean = true,
    precision: TimeUnit = MILLISECONDS
  ): Future[Unit] = {
    val data = WriteProtocol.write(precision, points: _*)
    logger.debug(s"storing data to $databaseName\n$data")
    val req = authenticatedUrlForDatabase(databaseName, Write, precision)
    req.post(data).flatMap { response =>
      logger.debug(response.toString)
      logger.debug(response.body)
      response.status match {
        case 404 if createDbIfNeeded =>
          // make sure we don't end up in an endless loop
          createDb(databaseName).flatMap(_ => storeAndMakeDbIfNeeded(databaseName, points, createDbIfNeeded = false))
        case 204 => // ok
          logger.info(s"stored ${points.length} points to $databaseName")
          Future.successful(())
        case other =>
          Future.failed(
            new RuntimeException(
              s"""Got status ${response.status} with body: ${response.body.stripLineEnd} when saving ${points.length} points to $databaseName """
            )
          )
      }
    }
  }

  def createDb(databaseName: String): Future[Unit] = {
    val q =
      s"""CREATE DATABASE "$databaseName" WITH DURATION $defaultRetention REPLICATION 1 NAME "${databaseName}_rp" """
    val url = s"$baseUrl/query"
    authenticatedUrl(url).addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded").post(s"q=$q").flatMap {
      response =>
        logger.info("status: " + response.status)
        logger.debug(response.headers.mkString("\n"))
        logger.debug(response.body)

        if (response.status != 200) {
          Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
        } else {
          Future.successful(())
        }
    }
  }
  private def getResultsFromResponse(response: StandaloneWSResponse): Future[Results] = {
    logger.debug("status: " + response.status)
    response.status match {
      case 200 => // ok
        logger.trace(s"got data\n${Json.prettyPrint(response.body[JsValue])}")
        import QueryResultProtocol._
        val results = response.body[JsValue].as[Results]
        if (results.hasErrors) {
          // possible errors:
          // too many points in the group by interval. maybe you forgot to specify a where time clause?
          Future.failed(new RuntimeException(results.allErrors.mkString(" | ")))
        } else {
          Future.successful(results)
        }
      case other =>
        Future.failed(new RuntimeException(s"Got status ${response.status} with body: ${response.body}"))
    }
  }
  private def authenticatedUrlForDatabase(databaseName: String, method: Method, precision: TimeUnit = MILLISECONDS) = {
    val influxPrecision = precision match {
      case MILLISECONDS => "ms"
      case _            => throw new RuntimeException(s"precision $precision not implemented")
    }

    val url = s"$baseUrl/${method.endpoint}"
    authenticatedUrl(url).addQueryStringParameters(
      "db"        -> databaseName,
      "precision" -> influxPrecision
    )
  }

  private def authenticatedUrlFor(method: Method) = {
    val url = s"$baseUrl/${method.endpoint}"
    authenticatedUrl(url)
  }

  private def authenticatedUrl(url: String) =
    ws.url(url).withRequestTimeout(INFLUX_REQUEST_TIMEOUT).withAuth(username, password, WSAuthScheme.BASIC)

}
