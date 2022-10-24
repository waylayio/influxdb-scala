package io.waylay.influxdb

import io.waylay.influxdb.Influx.Serie
import org.specs2.mutable.Specification
import play.api.libs.json.Json.{arr, obj}

class QueryResultProtocolSpec extends Specification {
  import io.waylay.influxdb.query.QueryResultProtocol._
  "the query protocol" should {
    "read series without name " in {
      val serieObj = obj("columns" -> arr("name", "duration"), "values" -> arr(arr("testdb", "672h")))
      val jsValue  = serieReads.reads(serieObj)
      jsValue.get.name mustEqual ""
    }

    "read series with existing name" in {
      val serieObj =
        obj(
          "name"    -> "temperature",
          "columns" -> arr("time", "mean"),
          "values"  -> arr(arr("1970-01-01T00:00:00Z", 20), arr("1970-01-01T00:02:00Z", 25))
        )
      val jsValue = serieReads.reads(serieObj)
      jsValue.get.name mustEqual "temperature"
    }
  }
}
