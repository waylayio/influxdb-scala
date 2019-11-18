package io.waylay.influxdb.query

import io.waylay.influxdb.Influx._
import play.api.libs.json._

object QueryResultProtocol {

  implicit val fieldValueReads = new Reads[IFieldValue] {

    override def reads(json: JsValue): JsResult[IFieldValue] = json match {
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
  }

  // not sure why we need this...
  implicit val optionFieldValueReads = new Reads[Option[IFieldValue]] {
    override def reads(json: JsValue): JsResult[Option[IFieldValue]] =
      json match {
        case JsNull => JsSuccess(None)
        case other  => fieldValueReads.reads(other).map(Some(_))
      }
  }

  implicit val serieReads = Json.reads[Serie]
  implicit val seriesReads = Json.reads[Result]
  implicit val resultsReads = Json.reads[Results]

}
