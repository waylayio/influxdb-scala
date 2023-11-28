package io.waylay.influxdb.query

import io.waylay.influxdb.Influx._
import io.waylay.influxdb.InfluxDB._
import io.waylay.influxdb.query.InfluxQueryBuilder.Order.{Ascending, Descending}
import io.waylay.influxdb.{InfluxDB, SharedProtocol}

import java.time.Instant

object InfluxQueryBuilder extends SharedProtocol {

  def simple(
    fields: Seq[String],
    tagSelector: (String, String),
    measurement: String,
    interval: Interval = Interval.beginningOfTimeUntilNow,
    order: Order = Order.defaultOrder,
    limit: Option[Long] = None
  ): String =
    simpleMultipleMeasurements(fields, tagSelector, Seq(measurement), interval, order, limit)

  def simpleMultipleMeasurements(
    fields: Seq[String],
    tagSelector: (String, String),
    measurements: Seq[String],
    interval: Interval = Interval.beginningOfTimeUntilNow,
    order: Order = Order.defaultOrder,
    limit: Option[Long] = None
  ): String = {
    val selects            = fields.map(escapeValue).mkString(", ")
    val measurementsString = measurements.map(escapeValue).mkString(", ")
    val timeWhere          = instantToWhereExpression(interval)

    s"""
       |SELECT $selects
       |FROM $measurementsString
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |${timeWhere.map("AND " + _).getOrElse("")}
       |${toOrderClause(order)}
       |${limit.fold("")(l => s"LIMIT $l")}
       |""".stripMargin.trim.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
  }

  def grouped(
    func: IFunction,
    tagSelector: (String, String),
    measurement: String,
    grouping: Duration,
    interval: Interval = Interval.beginningOfTimeUntilNow,
    filter: Option[IFilter] = None,
    limit: Option[Long] = None
  ): String = groupedMultiple(Seq(func), tagSelector, measurement, grouping, interval, filter, limit)

  /**
   * Variation of grouped that can take multiple IFunctions
   */
  def groupedMultiple(
    funcs: Seq[IFunction],
    tagSelector: (String, String),
    measurement: String,
    grouping: Duration,
    interval: Interval = Interval.beginningOfTimeUntilNow,
    filter: Option[IFilter] = None,
    limit: Option[Long] = None
  ): String = {

    if (funcs.isEmpty) {
      throw new IllegalArgumentException("At least one function must be supplied")
    }

    val timeWhere        = instantToWhereExpression(interval)
    val fieldFilterWhere = filter.map(filterToWhereExpressions)
    // TODO validate that timeWhere is not None?

    s"""
       |SELECT ${funcs.map(functionToSelect).mkString(", ")}
       |FROM ${escapeValue(measurement)}
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |${timeWhere.map("AND " + _).getOrElse("")}
       |${fieldFilterWhere.map("AND (" + _ + ")").getOrElse("")}
       |GROUP BY time(${durationLiteral(grouping)})
       |${limit.map(l => "\nLIMIT " + l).getOrElse("")}
       |""".stripMargin.trim.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
    // WHERE time > 1434059627s
    // WHERE time > '2013-08-12 23:32:01.232' AND time < '2013-08-13';
    // WHERE time > now() - 1h
    // limit 1000;
  }

  def dropSeries(tagSelector: (String, String)): String =
    s"""
       |DROP SERIES
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |""".stripMargin.trim

  def deleteSeries(tagSelector: (String, String), from: Option[Instant], until: Option[Instant]): String =
    s"""
       |DELETE
       |WHERE ${escapeValue(tagSelector._1)}=${escapeStringLiteral(tagSelector._2)}
       |""".stripMargin.trim + from.fold("")(i => s" AND time >= ${i.toEpochMilli}ms") + until.fold("")(i =>
      s" AND time <= ${i.toEpochMilli}ms"
    )

  def deleteSeriesPredicate(tagSelector: (String, String)): String =
    s"""
       |${escapeValue(tagSelector._1)}=${escapeValue(tagSelector._2)}
       |""".stripMargin.trim

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
  private def escapeStringLiteral(tag: String) =
    "'" + tag.replace("'", "\'") + "'"

  def metricNames(database: String, tagSelector: (String, String)) =
    s"""SHOW TAG KEYS ON "$database" WHERE ${tagSelector._1} = '${tagSelector._2}'"""

  private def toOrderClause(order: Order) = {
    val orderDirective = order match {
      case Ascending  => "ASC"
      case Descending => "DESC"
    }
    s"ORDER BY time $orderDirective"
  }

  import InfluxDB._

  private def instantToExpression(instant: IInstant): String = instant match {
    case Now                          => "now()"
    case Exact(inst)                  => inst.toEpochMilli.toString + "ms" // TODO do we want to go more precise?
    case RelativeTo(to, timeToGoBack) => instantToExpression(to) + " - " + durationLiteral(timeToGoBack)
  }

  private def functionToSelect(iFunction: IFunction): String = iFunction match {
    case Count(Left(field)) => s"""COUNT(${escapeValue(field)})"""
    case Count(Right(func)) => s"""COUNT(${functionToSelect(func)})"""
    case Min(field)         => s"""MIN(${escapeValue(field)})"""
    case Max(field)         => s"""MAX(${escapeValue(field)})"""
    case Mean(field)        => s"""MEAN(${escapeValue(field)})"""
    case Median(field)      => s"""MEDIAN(${escapeValue(field)})"""
    case Distinct(field)    => s"""DISTINCT(${escapeValue(field)})"""
    case Sum(field)         => s"""SUM(${escapeValue(field)})"""
    case Stddev(field)      => s"""STDDEV(${escapeValue(field)})"""
    case Last(field)        => s"""LAST(${escapeValue(field)})"""
    case First(field)       => s"""FIRST(${escapeValue(field)})"""
    case other              => throw new RuntimeException("not implemented: " + other.toString)
  }

  private def instantToWhereExpression(interval: Interval): Option[String] =
    interval match {
      case Interval(None, None)        => None
      case Interval(Some(start), None) => Some("time >= " + instantToExpression(start))
      case Interval(None, Some(end))   => Some("time < " + instantToExpression(end))
      case Interval(Some(start), Some(end)) =>
        Some("time >= " + instantToExpression(start) + " AND time < " + instantToExpression(end))
    }

  private def operatorString(op: IFieldFilterOperation): String = op match {
    case InfluxDB.EQ  => "="
    case InfluxDB.NE  => "<>"
    case InfluxDB.LT  => "<"
    case InfluxDB.LTE => "<="
    case InfluxDB.GT  => ">"
    case InfluxDB.GTE => ">="
  }

  private def escapedFieldValue(value: IFieldValue): String = value match {
    case IString(stringValue) => escapeStringLiteral(stringValue)
    case IBoolean(bool)       => bool.toString
    case IInteger(intVal)     => intVal.toString
    case IFloat(doubleVal)    => doubleVal.toString

  }

  private def fieldFilterToWhereExpression(filter: IFieldFilter) =
    s"${escapeValue(filter.field_key)} ${operatorString(filter.operator)} ${escapedFieldValue(filter.value)}"

  private def filterToWhereExpressions(filter: IFilter): String = filter match {
    case f: IFieldFilter =>
      fieldFilterToWhereExpression(f)
    case AND(f1, f2, others @ _*) =>
      (Seq(f1, f2) ++ others).map(f => s"(${filterToWhereExpressions(f)})").mkString(" AND ")
    case OR(f1, f2, others @ _*) =>
      (Seq(f1, f2) ++ others).map(f => s"(${filterToWhereExpressions(f)})").mkString(" OR ")
    case NOT(filter) =>
      s"NOT (${filterToWhereExpressions(filter)})"
  }

  sealed trait IInstant

  sealed trait Order

  case class Exact(instant: Instant) extends IInstant

  case class RelativeTo(to: IInstant = Now, timeToGoBack: Duration) extends IInstant

  case class Interval(start: Option[IInstant], end: Option[IInstant]) {
    def toMillis: Long = this match {
      case Interval(Some(Exact(from)), Some(Exact(until))) => until.toEpochMilli - from.toEpochMilli
      case other                                           => 0
    }
  }

  object Now extends IInstant

  object Order {
    val defaultOrder: Order = Ascending

    case object Ascending extends Order

    case object Descending extends Order
  }

  /**
   * If start and end times aren’t set they will default to beginning of time until now, respectively.
   */
  object Interval {
    val beginningOfTimeUntilNow: Interval = Interval(None, None)

    def fromJava(start: Option[java.time.Instant], end: Option[java.time.Instant]): Interval =
      Interval(start.map(Exact), end.map(Exact))

    def from(start: Instant): Interval                    = Interval(Some(Exact(start)), None)
    def fromUntil(start: Instant, end: Instant): Interval = Interval(Some(Exact(start)), Some(Exact(end)))
    def relativeToNow(timeToGoBack: Duration): Interval   = Interval(Some(RelativeTo(Now, timeToGoBack)), None)
    def relativeTo(timeToGoBack: Duration, to: Instant): Interval =
      Interval(Some(RelativeTo(Exact(to), timeToGoBack)), None)
  }
}
