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
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def getEmptyOptions: Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      val paramVal: String = request.body.get("dataType").map(_.head).getOrElse("")
      val response = Json.stringify(getOptions(paramVal))
      Ok(response)
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
        lines_list = lines_list.dropRight(2)
        commaOrnNot = "\t,"
      }

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
      var pathFound = false

      optionsBits.zipWithIndex.foreach {
        case (item, index) =>
          val kvbits = item.split(",", 2)
          if (kvbits(0) == "\"path\"") {
            src = kvbits(1)
            pathFound = true
          } else if (kvbits(0) == "\"entity\"") {
            entity = kvbits(1)
          } else {
            srcOptsToAppend = "%s\n\t\t\t%s: %s".format(srcOptsToAppend, kvbits(0), kvbits(1))
            if (index < optionsBits.length - 1) {
              srcOptsToAppend = srcOptsToAppend + ","
            }
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

      pw.write("\n\t]\n}")
      pw.close()

      Ok(srcOptsToAppend)
  }

  def newMappings: Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      val mappings: String = request.body.get("mappings").map(_.head).getOrElse("")
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
      val cursor = database.get_cursor("mappings", Filters.eq("entity", entity))
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
      prefixMap += ("ql" -> "http://semweb.mmlab.be/ns/ql#")
      prefixMap += ("ex" -> "http://example.com/ns#")
      prefixMap += ("rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
      prefixMap += ("rdfs" -> "http://www.w3.org/2000/01/rdf-schema#")
      prefixMap += ("xsd" -> "http://www.w3.org/2001/XMLSchema#")
      prefixMap += ("ex" -> "http://example.com/ns#")
      //  prefixMap += (shortns_clss -> ns_clss)

      val mp = JSONstringToMap(mappings)
      mp.foreach {
        case (k, v) =>
          if (pk != k.replace("\"", "")) {
            val vbits = v.replace("\"", "").split("___") // eg. gd___http://purl.org/goodrelations/v1#legalName
            val short_ns = vbits(0) // eg. gd
            val pred_url = vbits(1) // eg. http://purl.org/goodrelations/v1#legalName
            val pred_url_bits = v.replace("#", "/").split("/")
            val pred = pred_url_bits(pred_url_bits.length - 1).replace("\"", "") // eg. legalName
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

  // Helping methods
  def JSONstringToMap(mapString: String): Map[String, String] = {
    val newString = omit(omit(mapString, "[["), "]]")
    val keyValueBits = newString.split("],\\[")
    var newMap: Map[String, String] = Map()

    keyValueBits.foreach(kv => {
      val kvbits = kv.split(",")
      val k = kvbits(0)
      val v = kvbits(1)
      newMap = newMap + (k -> v)
    })
    newMap
  }

  def omit(str: String, omt: String): String = {
    str.replace(omt, "")
  }

  def getPredicates(p: String, hasP: Option[Seq[String]]): Action[AnyContent] = Action {

    import org.dizitart.no2.Document

    val cursor = database.get_cursor("mapping", null)
    val projection = Document.createDocument("propertiesMap", null).put("prolog", null)
    val documents = cursor.project(projection)
    var suggestedPredicates: Set[String] = Set()

    val value: Seq[String] = hasP match { // Predicated entered by the user in the query
      case None => Seq() // Or handle the lack of a value another way: throw an error, etc.
      case Some(s: Seq[String]) => s // Return the string to set your value
    }

    documents.forEach(d => {
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
    })
    Ok(Json.stringify(Json.toJson(suggestedPredicates)))
  }

  def getClasses(c: String): Action[AnyContent] = Action {
    import org.dizitart.no2.filters.Filters

    var suggestedClasses: Set[String] = Set()
    val cursor = database.get_cursor("mapping", Filters.regex("class", c))

    cursor.forEach(document => {
      val prologMap = document.get("prolog").asInstanceOf[HashMap[String, String]]
      val clss = document.get("class").toString
      val bits = clss.split(":")
      val namespaceAbbreviation = bits(0)
      val clssName = bits(1)
      val cl = clssName + " (" + prologMap(namespaceAbbreviation) + ")"
      if (clssName.contains(c))
        suggestedClasses += namespaceAbbreviation + ":" + cl
    })
    Ok(Json.stringify(Json.toJson(suggestedClasses)))
  }

  def generateMappings: Action[AnyContent] = Action {
    val cursor = database.get_cursor("mappings", null)
    var rml = ""
    var allPrefixes: HashMap[String, String] = new HashMap()

    cursor.forEach(document => {
      val prolog = document.get("prolog").asInstanceOf[HashMap[String, String]]
      val entity = document.get("entity").toString
      val source = document.get("source").toString
      database.csv_file_path = source
      val clss = if (document.get("class") != null) document.get("class").toString
      val id = document.get("ID").toString
      val dtype = document.get("type").toString
      val propertiesMap = document.get("propertiesMap").asInstanceOf[HashMap[String, String]]

      prolog.foreach(pre => allPrefixes += (pre._1 -> pre._2))

      rml = rml + "\n\n<#" + entity + "Mapping> a rr:TriplesMap;"
      rml = rml + "\n\trml:logicalSource ["
      rml = rml + "\n\t\trml:source \"" + source + "\";"
      rml = rml + "\n\t\trml:referenceFormulation ql:" + dtype.toUpperCase
      rml = rml + "\n\t];"
      rml = rml + "\n\trr:subjectMap ["
      rml = rml + "\n\t\trr:template \"http://example.com/{" + id + "}\";"
      rml = rml + "\n\t\trr:class " + clss
      rml = rml + "\n\t];\n"

      propertiesMap.zipWithIndex.foreach {
        case (item, index) =>
          rml = rml + "\n\trr:predicateObjectMap ["
          rml = rml + "\n\t\trr:predicate " + item._1 + ";"
          if (index < propertiesMap.size - 1) {
            rml = rml + "\n\t\trr:objectMap [rml:reference " + item._2 + "]];"
          } else {
            rml = rml + "\n\t\trr:objectMap [rml:reference " + item._2 + "]]."
          }
      }
    })

    allPrefixes.foreach { item => {
      if (item._1 == "ex") {
        rml = "@base <" + item._2 + ">.\n" + rml
      } else {
        rml = "@prefix " + item._1 + ": <" + item._2 + ">.\n" + rml
      }
    }
    }
    database.rml_text = rml
    Ok(Json.toJson(Json.obj(
      "csv_file_path" -> database.csv_file_path,
      "rml_text" -> database.rml_text,
      "instance_name" -> database.instance_name,
      "branch_name" -> database.branch_name
    )))
  }
}