package io.waylay.influxdb

private[influxdb] trait SharedProtocol {

  /**
   * Strings are text values. All string values must be surrounded in double-quotes ".
   * If the string contains a double-quote, it must be escaped with a backslash, e.g. \".
   *
   * @param string the string to escape
   * @return the escape string
   */
  def escapeValue(string: String): String =
    "\"" + string.replace("\"", "\\\"") + "\""

}
