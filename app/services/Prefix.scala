package services

import scala.collection.immutable.HashMap

class Prefix {
  var entity: String = _
  var source: String = _
  var clss: Any = _
  var id: String = _
  var dtype: String = _
  var propertiesMap: Map[String, String] = _
  var prefixMap: Map[String, String] = _
}

class Person {

  var name: String = _

  var age: Int = _

}