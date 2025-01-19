package io.waylay.influxdb

import java.time.Instant

/**
 * Influx related models
 */
object Influx {

  type Version = String

  sealed trait IFieldValue
  case class IInteger(value: Long)    extends IFieldValue
  case class IFloat(value: Double)    extends IFieldValue
  case class IBoolean(value: Boolean) extends IFieldValue
  case class IString(value: String)   extends IFieldValue

  case class IPoint(
    measurementName: String,
    tags: Seq[(String, String)],
    fields: Seq[(String, IFieldValue)],
    timestamp: Instant
  )

  case class Serie(
    name: String,
    tags: Option[Map[String, String]],
    columns: Seq[String],
    values: Option[Seq[Seq[Option[IFieldValue]]]]
  )

  // we could probably split this up in SeriesResult and ErrorResult
  case class Result(
    series: Option[Seq[Serie]],
    error: Option[String]
  )

  // we should probably split this up in QueryResults and QueryError
  case class Results(
    results: Option[Seq[Result]],
    error: Option[String]
  ) {
    lazy val allErrors: Seq[String] =
      error.toSeq ++ results.getOrElse(Seq.empty).flatMap(_.error)

    lazy val hasErrors: Boolean                = allErrors.nonEmpty
    lazy val hasDatabaseNotFoundError: Boolean = allErrors.exists(_.contains("database not found"))
  }
  //  {
  //    "results" : [ {
  //      "series" : [ {
  //        "name" : "CO2",
  //        "columns" : [ "time", "resource", "value" ],
  //        "values" : [
  //          [ "2015-09-08T08:37:32Z", "Living", 706 ],
  //          [ "2015-09-08T08:47:39Z", "Living", 723 ],
  //          [ "2015-09-08T08:57:45Z", "Living", 700 ],
  //          [ "2015-09-08T12:37:46Z", "Living", 472 ] ]
  //      } ]
  //    } ]
  //  }
  //
  //  {
  //    "results" : [ {
  //      "error" : "database not found: demo.waylay.io"
  //    } ]
  //  }

}
