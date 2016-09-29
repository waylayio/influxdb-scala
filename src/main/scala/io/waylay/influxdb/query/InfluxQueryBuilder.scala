package io.waylay.influxdb.query

import java.time.Instant

import io.waylay.influxdb.{SharedProtocol, InfluxDB}
import io.waylay.influxdb.InfluxDB._

object InfluxQueryBuilder extends SharedProtocol{


  sealed trait IInstant
  object Now extends IInstant
  case class Exact(instant: Instant) extends IInstant
  case class RelativeTo(to: IInstant = Now, timeToGoBack: Duration) extends IInstant



  /**
   * If start and end times aren’t set they will default to beginning of time until now, respectively.
   */
  object Interval{
    def fromJava(start: Option[java.time.Instant], end: Option[java.time.Instant]) = {
      Interval(start.map(Exact), end.map(Exact))
    }
    val beginningOfTimeUntilNow = Interval(None, None)
    def from(start: Instant) = Interval(Some(Exact(start)), None)
    def fromUntil(start: Instant, end: Instant) = Interval(Some(Exact(start)), Some(Exact(end)))
    def relativeToNow(timeToGoBack: Duration) = Interval(Some(RelativeTo(Now, timeToGoBack)), None)
    def relativeTo(timeToGoBack: Duration, to: Instant) = Interval(Some(RelativeTo(Exact(to), timeToGoBack)), None)
  }
  case class Interval(start: Option[IInstant], end: Option[IInstant]){
    def toMillis: Long = this match {
      case Interval(Some(Exact(from)), Some(Exact(until))) => until.toEpochMilli - from.toEpochMilli
      case other => ???
    }
  }



  import InfluxDB._

  def simple(fields: Seq[String], tagSelector: (String, String), measurement: String, interval: Interval = Interval.beginningOfTimeUntilNow) = {
    val selects = fields.map(field => escapeValue(field)).mkString(", ")

    val timeWhere = instantToWhereExpression(interval)

    s"""
       |SELECT $selects
       |FROM ${escapeValue(measurement)}
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |${timeWhere.map("AND " + _).getOrElse("")}
       |""".stripMargin.trim
  }

  def grouped(
    func: IFunction,
    tagSelector: (String, String),
    measurement: String,
    grouping: Duration,
    interval: Interval = Interval.beginningOfTimeUntilNow,
    limit: Option[Long] = None
  ) = {

    val timeWhere = instantToWhereExpression(interval)
    // TODO validate that timeWhere is not None?

    s"""
       |SELECT ${functionToSelect(func)}
       |FROM ${escapeValue(measurement)}
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |${timeWhere.map("AND " + _).getOrElse("")}
       |GROUP BY time(${durationLiteral(grouping)})
       |""".stripMargin.trim + limit.map(l => "\nLIMIT " + l).getOrElse("")


    // WHERE time > 1434059627s
    // WHERE time > '2013-08-12 23:32:01.232' AND time < '2013-08-13';
    // WHERE time > now() - 1h
    // limit 1000;
  }

  def dropSeries(tagSelector: (String, String)) = {
    s"""
       |DROP SERIES
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |""".stripMargin.trim
  }



  /**
   * String Literals (Single-quoted)
   * String literals are values (like integers or booleans). In InfluxQL, all tag values are string literals, and any
   * field values that are not integers, floats, or booleans are also strings. String literals are also used when
   * checking equality with identifiers.
   * String Literal Quoting Requirements
   * String literals must always be single-quoted ('). String literals may contain any unicode characters except
   * for single quotes, new lines and backslashes, which must be backslash (\) escaped.
 *
   * @param tag the tag to escape
   * @return the escaped tag
   */
  private def escapeStringLiteral(tag: String) = {
    "'" + tag.replace("'","\'") + "'"
  }



  private def instantToExpression(instant: IInstant):String = instant match {
    case Now => "now()"
    case Exact(inst) => inst.toEpochMilli.toString + "ms" // TODO do we want to go more precise?
    case RelativeTo(to, timeToGoBack) => instantToExpression(to) + " - " + durationLiteral(timeToGoBack)
  }

  private def functionToSelect(iFunction: IFunction):String = iFunction match{
    case Count(Left(field)) => s"""COUNT(${escapeValue(field)})"""
    case Count(Right(func)) => s"""COUNT(${functionToSelect(func)})"""
    case Min(field)         => s"""MIN(${escapeValue(field)})"""
    case Max(field)         => s"""MAX(${escapeValue(field)})"""
    case Mean(field)        => s"""MEAN(${escapeValue(field)})"""
    case Median(field)      => s"""MEDIAN(${escapeValue(field)})"""
    case Distinct(field)    => s"""DISTINCT(${escapeValue(field)})"""
    case Sum(field)         => s"""SUM(${escapeValue(field)})"""

    case other => throw new RuntimeException("not implemented: " + other.toString)
  }

  private def instantToWhereExpression(interval: Interval): Option[String] = {
    interval match {
      case Interval(None, None) => None
      case Interval(Some(start), None) => Some("time >= " + instantToExpression(start))
      case Interval(None, Some(end)) => Some("time < " + instantToExpression(end))
      case Interval(Some(start), Some(end)) => Some("time >= " + instantToExpression(start) + " AND time < " + instantToExpression(end))
    }
  }
}
