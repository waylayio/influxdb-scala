package io.waylay.influxdb.query

import io.waylay.influxdb.Influx._
import play.api.libs.json._

object QueryResultProtocol {

  implicit val fieldValueReads: Reads[IFieldValue] = {
    // writing ints where previousy we wrote doubles is not possible
    // also 23.0 might be seen as an int where it should not be one
    // therefore we write floats only
    //        case n: JsNumber if n.value.isValidLong =>
    //          JsSuccess(IInteger(n.value.longValue()))
    case n: JsNumber =>
      JsSuccess(IFloat(n.value.doubleValue))
    case s: JsString =>
      JsSuccess(IString(s.value))
    case b: JsBoolean =>
      JsSuccess(IBoolean(b.value))
    case other =>
      JsError("Can not read field format of type type" + other)
  }

  // not sure why we need this...
  implicit val optionFieldValueReads: Reads[Option[IFieldValue]] = {
    case JsNull => JsSuccess(None)
    case other  => fieldValueReads.reads(other).map(Some(_))
  }

  implicit val serieReads: Reads[Serie] = jsValue =>
    for {
      name    <- (jsValue \ "name").validateOpt[String]
      tags    <- (jsValue \ "tags").validateOpt[Map[String, String]]
      columns <- (jsValue \ "columns").validate[Seq[String]]
      values  <- (jsValue \ "values").validateOpt[Seq[Seq[Option[IFieldValue]]]]
    } yield Serie(name.getOrElse(""), tags, columns, values)

  implicit val seriesReads: Reads[Result]   = Json.reads[Result]
  implicit val resultsReads: Reads[Results] = Json.reads[Results]

}
