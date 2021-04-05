package controllers

import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import services.MappingsDB

import java.io._
import javax.inject._
import scala.collection.immutable.HashMap
import scala.io.Source

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class AjaxController @Inject()(cc: ControllerComponents, playconfiguration: Configuration, database: MappingsDB) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  val logger:Logger = LoggerFactory.getLogger(this.getClass)
  def getEmptyOptions: Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      val paramVal: String = request.body.get("dataType").map(_.head).getOrElse("")

      val response = Json.stringify(getOptions(paramVal))

      Ok(response)
  }

  def setOptions(): Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      var options: String = request.body.get("options").map(_.head).getOrElse("")
      val srcType: String = request.body.get("srcType").map(_.head).getOrElse("")

      var srcOptsToAppend = ""
      val sourcesConfFile = playconfiguration.underlying.getString("sourcesConfFile")

      val configs = Source.fromFile(sourcesConfFile)
      var lines_list = try configs.getLines().toList finally configs.close()
      var commaOrnNot = ""
      if (lines_list.isEmpty)
        srcOptsToAppend = srcOptsToAppend + "{\n\t\"sources\": [\n"
      else {
        lines_list = lines_list.dropRight(2) // Delete last 2 lines "] \n }"
        commaOrnNot = "\t,"
      }

      //new_lines = lines_list

      val pw = new PrintWriter(new File(sourcesConfFile))
      lines_list.foreach(l => pw.write(l + "\n"))

      // To open the end of the file to new entry
      options = omit(options, "[[")
      options = omit(options, "]]")
      val optionsBits = options.split("],\\[")

      srcOptsToAppend = srcOptsToAppend + commaOrnNot + "\t{"
      srcOptsToAppend = srcOptsToAppend + "\n\t\t\"type\": \"" + srcType + "\","

      srcOptsToAppend = srcOptsToAppend + "\n\t\t\"options\": {"

      var src = ""
      var entity = ""
      var pathFound = false // To defrentiate between file-based sources (eg. CSV/Paruqet) and db based (eg. MongoDB, Cassandra)
      for (kv <- 0 until optionsBits.length - 1) {
        val kvbits = optionsBits(kv).split(",", 2) // TODO: carefull where , used as csv delimiter
        //var optValue = (kvbits(0) -> kvbits(1))
        if (kvbits(0) == "\"path\"") { // move out of the options
          src = kvbits(1)
          pathFound = true
        } else if (kvbits(0) == "\"entity\"") // move out of the options
          entity = kvbits(1)
        else {
          srcOptsToAppend = srcOptsToAppend + "\n\t\t\t" + kvbits(0) + ": " + kvbits(1)

          if (kv < optionsBits.length - 1)
            srcOptsToAppend = srcOptsToAppend + ","
        }
      }

      srcOptsToAppend = srcOptsToAppend + "\n\t\t},"
      if (pathFound)
        srcOptsToAppend = srcOptsToAppend + "\n\t\t\"source\": " + src + ","
      else
        srcOptsToAppend = srcOptsToAppend + "\n\t\t\"source\": " + entity.replaceFirst("\"", "\"//") + "," // When it's not a file, source will be: "//[Entity]" just a randomly-selecte template, can change in the future
      srcOptsToAppend = srcOptsToAppend + "\n\t\t\"entity\": " + entity
      srcOptsToAppend = srcOptsToAppend + "\n\t}"

      pw.write(srcOptsToAppend)

      pw.write("\n\t]\n}") // put back the two lines
      pw.close()

      Ok(srcOptsToAppend)
  }

  def newMappings: Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      val mappings: String = request.body.get("mappings").map(_.head).getOrElse("") // javalangstring
      val pk: String = request.body.get("pk").map(_.head).getOrElse("")
      val clss: String = request.body.get("clss").map(_.head).getOrElse("")
      val shortns_clss: String = request.body.get("shortns_clss").map(_.head).getOrElse("")
      val ns_clss: String = request.body.get("ns_clss").map(_.head).getOrElse("")
      val entity: String = request.body.get("entity").map(_.head).getOrElse("")
      val src: String = request.body.get("src").map(_.head).getOrElse("")
      val dtype: String = request.body.get("dtype").map(_.head).getOrElse("")

      import org.dizitart.no2.Document
      import org.dizitart.no2.filters.Filters

      var propertiesMap: HashMap[String, String] = new HashMap()
      var prefixMap: HashMap[String, String] = new HashMap()

      val cursor = database.get_cursor("mappings",Filters.eq("entity", entity))
      var returnMsg = ""
      val collection = database.get_collection("mappings")
      if (cursor.size() > 0) {
        collection.remove(Filters.eq("entity", entity))
        returnMsg = "Entity already exists, it has been overwritten"
      } else {
        returnMsg = "Entity added"
      }

      prefixMap += ("rr" -> "http://www.w3.org/ns/r2rml#")
      prefixMap += ("rml" -> "http://semweb.mmlab.be/ns/rml#")
      prefixMap += ("nosql" -> "http://purle.org/db/mysql#")
      prefixMap += (shortns_clss -> ns_clss)

      val mp = JSONstringToMap(mappings)
      for ((k, v) <- mp) {
        if (pk != k.replace("\"", "")) {
          val vbits = v.replace("\"", "").split("___") // eg. gd___http://purl.org/goodrelations/v1#legalName
          val short_ns = vbits(0) // eg. gd
          val pred_url = vbits(1) // eg. http://purl.org/goodrelations/v1#legalName
          val pred_urlbits = v.replace("#", "/").split("/")
          val pred = pred_urlbits(pred_urlbits.length - 1).replace("\"", "") // eg. legalName
          val ns = pred_url.replace(pred, "") // eg. http://purl.org/goodrelations/v1#

          propertiesMap += (short_ns + ":" + pred -> k)
          prefixMap += (short_ns.replace("\"", "") -> ns)
        }
      }

      val doc = Document.createDocument("entity", entity)
        .put("prolog", prefixMap)
        .put("source", src)
        .put("type", dtype)
        .put("propertiesMap", propertiesMap)

      if (dtype == "mongodb")
        doc.put("ID", "_id")
      else
        doc.put("ID", pk.replace("\"", ""))

      if (clss != "")
        doc.put("class", shortns_clss + ":" + clss.replace(ns_clss, ""))

      collection.insert(doc)

      Ok(Json.stringify(Json.toJson(returnMsg)))
  }

  def getPredicates(p: String, hasP: Option[Seq[String]]): Action[AnyContent] = Action {

    import org.dizitart.no2.Document
    import scala.jdk.CollectionConverters._

    val cursor = database.get_cursor("mapping",null)
    val projection = Document.createDocument("propertiesMap", null).put("prolog", null)
    val documents = cursor.project(projection)
    var suggestedPredicates: Set[String] = Set()

    val value: Seq[String] = hasP match { // Predicated entered by the user in the query
      case None => Seq() // Or handle the lack of a value another way: throw an error, etc.
      case Some(s: Seq[String]) => s // Return the string to set your value
    }


    for (d <- documents.toList.asScala) {
      val predicatesMap = d.get("propertiesMap").asInstanceOf[HashMap[String, String]]
      val prolog = d.get("prolog").asInstanceOf[HashMap[String, String]]
      var predicates = predicatesMap.keys
      val has = value.map(_.split("\\s\\(")(0)) // E.g. get "npg:date" from "npg:date (http://ns.nature.com/terms/)""
      /*if (has.nonEmpty && predicates.containsAll(has)) { // Suggest predicate from entities having all the already-entered predicates in the query

      } else if (has.isEmpty) {

      }*/

      predicates = predicates.map(pr => {
        val prBits = pr.split(":")
        val namespaceAbbreviation = prBits(0)

        pr + " (" + prolog(namespaceAbbreviation) + ")"
      })
      suggestedPredicates = suggestedPredicates ++ predicates.filter(x => x.split(":")(1).contains(p) || x.split(":")(1).contains(p.toLowerCase))
    }

    Ok(Json.stringify(Json.toJson(suggestedPredicates)))
  }

  def getClasses(c: String): Action[AnyContent] = Action {
    import org.dizitart.no2.filters.Filters
    import scala.jdk.CollectionConverters._

    var suggestedClasses: Set[String] = Set()

    val cursor = database.get_cursor("mapping",Filters.regex("class", c))
    for (document <- cursor.toList.asScala) {
      val prologMap = document.get("prolog").asInstanceOf[HashMap[String, String]]
      val clss = document.get("class").toString
      val bits = clss.split(":")
      val namespaceAbbreviation = bits(0)
      val clssName = bits(1)

      val cl = clssName + " (" + prologMap(namespaceAbbreviation) + ")"

      if (clssName.contains(c)) // don't auto-suggest based on the namespace
        suggestedClasses += namespaceAbbreviation + ":" + cl
    }
    Ok(Json.stringify(Json.toJson(suggestedClasses)))
  }

  def generateMappings: Action[AnyContent] = Action {
    val cursor = database.get_cursor("mappings",null)
    var rml = ""
    var allPrefixes: HashMap[String, String] = new HashMap()

    val it = cursor.iterator()
    while (it.hasNext) {
      val document = it.next()

      val prolog = document.get("prolog").asInstanceOf[HashMap[String, String]]
      val entity = document.get("entity").toString
      val source = document.get("source").toString
      val clss = if (document.get("class") != null) document.get("class").toString
      val id = document.get("ID").toString
      val dtype = document.get("type").toString
      val propertiesMap = document.get("propertiesMap").asInstanceOf[HashMap[String, String]]

      for (pre <- prolog) {
        allPrefixes += (pre._1 -> pre._2)
      }

      rml = rml + "\n\n<#" + entity + "Mapping>"
      rml = rml + "\n\trml:logicalSource ["
      rml = rml + "\n\t\trml:source \"" + source + "\";"; // eg. hdfs://localhost:9000/thesis/insurance_test.csv
      rml = rml + "\n\t\tnosql:Store nosql:" + dtype
      rml = rml + "\n\t];"

      rml = rml + "\n\trr:subjectMap ["
      rml = rml + "\n\t\trr:template \"http://example.com/{" + id + "}\";"
      rml = rml + "\n\t\trr:class " + clss
      rml = rml + "\n\t];\n"

      for (pm <- propertiesMap) {
        rml = rml + "\n\trr:predicateObjectMap ["
        rml = rml + "\n\t\trr:predicate " + pm._1 + ";"
        rml = rml + "\n\t\trr:objectMap [rml:reference " + pm._2 + "]"
      }
      rml = rml + "."
    }

    for (pre <- allPrefixes) {
      rml = "@prefix " + pre._1 + ": <" + pre._2 + ">\n" + rml
    }

    Ok(rml)
  }

  // Helping methods
  def JSONstringToMap(mapString: String): Map[String, String] = {
    var newString = omit(mapString, "[[")
    newString = omit(newString, "]]")

    val keyValueBits = newString.split("],\\[")
    var newMap: Map[String, String] = Map()

    for (kv <- keyValueBits) {
      val kvbits = kv.split(",")
      val k = kvbits(0)
      val v = kvbits(1)
      newMap = newMap + (k -> v)
    }
    newMap
  }

  def getOptions(choice: Any): JsObject = choice match {
    case "csv" =>
      Json.obj("path" -> Json.arr("", "Location of the file"), "header" -> Json.arr("true", "false", "Specify whether to consider the text header or add a personalized header"), "delimiter" -> Json.arr("", "Delimiter of the columns"), "mode" -> Json.arr("PERMISSIVE", "DROPMALFORMED", "FAILFAST", "Dealing with corrupt records during parsing"))
    case "parquet" =>
      Json.obj("path" -> Json.arr("", "Location of the file"), "spark_sql_parquet_filterPushdown" -> Json.arr("true", "false", "Enables Parquet filter push-down optimization when set to true."))
    case "mongodb" =>
      Json.obj("url" -> Json.arr("", ""), "database" -> Json.arr("", ""), "collection" -> Json.arr("", ""))
    case "cassandra" =>
      Json.obj("keyspace" -> Json.arr("", ""), "table" -> Json.arr("", ""))
    case "jdbc" =>
      Json.obj("url" -> Json.arr("", ""), "driver" -> Json.arr("", ""), "dbtable" -> Json.arr("", ""), "user" -> Json.arr("", ""), "password" -> Json.arr("", ""))
    case _ =>
      Json.obj("more" -> "to come")
  }

  def omit(str: String, omt: String): String = {
    str.replace(omt, "")
  }
}