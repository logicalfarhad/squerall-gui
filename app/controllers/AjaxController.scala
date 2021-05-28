package controllers

import org.dizitart.no2.Document
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import services.MappingsDB
import java.io.File
import javax.inject._
import scala.collection.immutable.HashMap

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class AjaxController @Inject()(cc: ControllerComponents, database: MappingsDB) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def setOptions(): Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) {
    request =>
      val sentity: String = request.body.get("entity").map(_.head).getOrElse("")
      val stype: String = request.body.get("type").map(_.head).getOrElse("")
      val source: String = request.body.get("source").map(_.head).getOrElse("")
      val delimiter: String = request.body.get("options[delimiter]").map(_.head).getOrElse("")
      val header: String = request.body.get("options[header]").map(_.head).getOrElse("")
      val mode: String = request.body.get("options[mode]").map(_.head).getOrElse("")
      val fileexists: Boolean = new File(source).exists()
      val collection = database.get_collection("mappings")
      request.session
        .get("connected")
        .map { _ =>
          if(fileexists){
            var mapObj: HashMap[String, String] = HashMap()
            mapObj += ("delimiter" -> delimiter)
            mapObj += ("header" -> header)
            mapObj += ("mode" -> mode)

            val config = Document.createDocument("config", null)
            config.put("entity", sentity)
            config.put("type", stype)
            config.put("source", source)
            config.put("options", mapObj)
            collection.insert(config)
          }
          Ok(Json.toJson({fileexists}))
        }.getOrElse {

        import org.dizitart.no2.filters.Filters
        collection.remove(Filters.ALL)
          Unauthorized("Oops, you are not connected")
        }
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