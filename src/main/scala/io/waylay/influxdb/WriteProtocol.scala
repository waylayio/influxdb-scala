package io.waylay.influxdb

import java.time.Instant

import io.waylay.influxdb.Influx._

import scala.concurrent.duration._

private[influxdb] object WriteProtocol extends SharedProtocol{

  def write(precision: TimeUnit, points: IPoint*): String = {
    // a DecimalFormat is not thread safe
    //    lazy val df = {
    //      // because we don' want scientific notation
    //      val df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
    //      //340 = DecimalFormat.DOUBLE_FRACTION_DIGITS
    //      df.setMaximumFractionDigits(340)
    //      df
    //    }

    val applyPrecision: Function[Instant, Long] = precision match {
      case SECONDS =>
        _.getEpochSecond
      case MILLISECONDS =>
        _.toEpochMilli
      case NANOSECONDS =>
        i => i.getEpochSecond * 1000000000 + i.getNano
      case _ =>
        throw new RuntimeException(s"precision $precision not implemented")
    }

    val lines = points.map{ point =>
      val measurementName = escapeTag(point.measurementName)
      val tags = point.tags.map{ case (key, value) =>
        "," + escapeTag(key) + "=" + escapeTag(value)
      }.mkString("")
      val fields = point.fields.map{ case (key, fieldValue) =>
        val stringValue = fieldValue match {
          case IInteger(value) => value.toString + "i"
          case IFloat(value) =>
            //"%g" format value
            // df.format(value)
            value.toString.replace('E', 'e')
          case IBoolean(value) => value.toString
          case IString(value) => escapeValue(value)
        }
        " " + escapeTag(key) + "=" + stringValue
      }.mkString(",")
      val timestamp = applyPrecision(point.timestamp)
      s"""$measurementName$tags$fields $timestamp"""
    }
    lines.mkString("\n")
  }

  /**
    * The key is the measurement name and any optional tags separated by commas. Measurement names, tag keys,
    * and tag values must escape any spaces or commas using a backslash (\).
    * For example: \ and \,. All tag values are stored as strings and should not be surrounded in quotes.
    *
    * @param tag the tag to escape
    * @return the escaped tag
    */
  def escapeTag(tag: String) = {
    tag.replace(" ","\\ ").replace(",", "\\,")
  }


}
