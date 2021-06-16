package controllers

import org.mongodb.scala.bson.BsonValue
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.{Document, MongoCollection}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import services.Helpers.GenericObservable
import services.{MappingsDB, Prefix}
import java.io.File
import javax.inject._
import scala.collection.immutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

@Singleton
class AjaxController @Inject()(cc: ControllerComponents, database: MappingsDB) extends AbstractController(cc) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val mongoCollection: MongoCollection[Document] = database.get_mongo_db_collection("mapping")

  def setOptions(): Action[Map[String, Seq[String]]] = Action.async(parse.tolerantFormUrlEncoded) { implicit request =>
    val sentity: String = request.body.get("entity").map(_.head).getOrElse("")
    val stype: String = request.body.get("type").map(_.head).getOrElse("")
    val source: String = request.body.get("source").map(_.head).getOrElse("")
    val delimiter: String = request.body.get("options[delimiter]").map(_.head).getOrElse("")
    val header: String = request.body.get("options[header]").map(_.head).getOrElse("")
    val mode: String = request.body.get("options[mode]").map(_.head).getOrElse("")
    val file_exists: Boolean = new File(source).exists()
    val session = request.session.get("session")
    if (session.isDefined) {
      if (file_exists) {
        var mapObj: HashMap[String, String] = HashMap()
        mapObj += ("delimiter" -> delimiter)
        mapObj += ("header" -> header)
        mapObj += ("mode" -> mode)
        val doc: Document = Document("entity" -> sentity,
          "type" -> stype,
          "source" -> source,
          "options" -> Document("delimiter" -> delimiter,
            "header" -> header,
            "mode" -> mode))
        mongoCollection.insertOne(doc).results()
        database.get_mongo_client.close()
      }
      Future.successful(Ok(toJson({
        file_exists
      })))
    } else {
      database.get_mongo_client.close()
      Future.successful(Redirect(routes.SquerallController.index()))
    }
  }

  def newMappings: Action[Map[String, Seq[String]]] = Action(parse.tolerantFormUrlEncoded) { implicit request =>
    val mappings: String = request.body.get("mappings").map(_.head).getOrElse("")
    val pk: String = request.body.get("pk").map(_.head).getOrElse("")
    val clss: String = request.body.get("clss").map(_.head).getOrElse("")
    val shortns_clss: String = request.body.get("shortns_clss").map(_.head).getOrElse("")
    val ns_clss: String = request.body.get("ns_clss").map(_.head).getOrElse("")
    val entity: String = request.body.get("entity").map(_.head).getOrElse("")
    val src: String = request.body.get("src").map(_.head).getOrElse("")
    val dtype: String = request.body.get("dtype").map(_.head).getOrElse("")

    var propertiesMap = Document()
    var returnMsg = ""

    mongoCollection.find(equal("entity", entity)).first().map(x => {
      returnMsg = "Entity added"
    }).printHeadResult("Entry added")

    var prefixMap = Document()
    prefixMap += ("rr" -> "http://www.w3.org/ns/r2rml#")
    prefixMap += ("rml" -> "http://semweb.mmlab.be/ns/rml#")
    prefixMap += ("ql" -> "http://semweb.mmlab.be/ns/ql#")
    prefixMap += ("ex" -> "http://example.com/ns#")
    // prefixMap += ("rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    // prefixMap += ("rdfs" -> "http://www.w3.org/2000/01/rdf-schema#")
    //prefixMap += ("xsd" -> "http://www.w3.org/2001/XMLSchema#")

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

    var document = Document()

    document += ("type" -> dtype)
    document += ("source" -> src)
    document += ("prolog" -> prefixMap)
    document += ("propertiesMap" -> propertiesMap)
    if (dtype == "mongodb")
      document += ("ID" -> "_id")
    else
      document += ("ID" -> pk.replace("\"", ""))
    if (clss != "")
      document += ("class" -> (shortns_clss + ":" + clss.replace(ns_clss, "")))

    val mod = Document("$set" -> document)
    mongoCollection.updateOne(equal("entity", entity), mod).printHeadResult("Update Result")
    database.get_mongo_client.close()
    Ok(stringify(toJson(returnMsg)))
  }

  // Helping methods
  def JSONstringToMap(mapString: String): Map[String, String] = {
    val newString = omit(omit(mapString, "[["), "]]")
    val keyValueBits = newString.split("],\\[")
    var newMap: Map[String, String] = Map()

    keyValueBits.foreach(kv => {
      val kv_bits = kv.split(",")
      val k = kv_bits(0)
      val v = kv_bits(1)
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
    Ok(stringify(toJson(suggestedPredicates)))
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
    Ok(stringify(toJson(suggestedClasses)))
  }

  def generateMappings: Action[AnyContent] = Action {

    //implicit request => {
    var rml = ""
    var prefixList = new ListBuffer[Prefix]()
    var prefixMap: Map[String, String] = Map()
    var propertiesMap: Map[String, String] = Map()

    var prefix: Prefix = null
    mongoCollection.find().map(document => {
      prefix = new Prefix()

      val prolog: Option[BsonValue] = document.get("prolog")
      val entity = document.getString("entity")
      val source = document.getString("source")
      database.csv_file_path = source
      val clss = if (document.getString("class") != null)
        document.getString("class")
      val id = document.getString("ID")
      val dtype = document.getString("type")
      val properties: Option[BsonValue] = document.get("propertiesMap")


      prefix.entity = entity
      prefix.source = source
      prefix.clss = clss
      prefix.id = id
      prefix.dtype = dtype


      prolog match {
        case Some(s) => s.asDocument().entrySet().forEach(x => {
          val opt_key = x.getKey
          val opt_val = x.getValue.asString().getValue
          prefixMap += (opt_key -> opt_val)
        })
        case None => println("Did not find anything");
      }
      prefix.prefixMap = prefixMap


      properties match {
        case Some(s) => s.asDocument().entrySet().forEach(x => {
          val opt_key = x.getKey
          val opt_val = x.getValue.asString().getValue
          propertiesMap += (opt_key -> opt_val)
        })
        case None => println("")
      }

      prefix.propertiesMap = propertiesMap
      prefixList += prefix
    }).printResults("Mapping Generated for multiple mapping objects")



    database.get_mongo_client.close()



    prefixList.indices.foreach(index => {
      rml = rml + "\n\n<#" + prefixList(index).entity + "Mapping> a rr:TriplesMap;"
      rml = rml + "\n\trml:logicalSource ["
      rml = rml + "\n\t\trml:source \"" + prefixList(index).source + "\";"
      rml = rml + "\n\t\trml:referenceFormulation ql:" + prefixList(index).dtype.toUpperCase
      rml = rml + "\n\t];"
      rml = rml + "\n\trr:subjectMap ["
      rml = rml + "\n\t\trr:template \"http://example.com/{" + prefixList(index).id + "}\";"
      rml = rml + "\n\t\trr:class " + prefixList(index).clss
      rml = rml + "\n\t];\n"


      prefixList(index).propertiesMap.zipWithIndex.foreach {
        case (item, i) =>
          rml = rml + "\n\trr:predicateObjectMap ["
          rml = rml + "\n\t\trr:predicate " + item._1 + ";"
          if (i < prefixList(index).propertiesMap.size - 1) {
            rml = rml + "\n\t\trr:objectMap [rml:reference " + item._2 + "]];"
          } else {
            rml = rml + "\n\t\trr:objectMap [rml:reference " + item._2 + "]]."
          }
      }

      prefixList(index).prefixMap.zipWithIndex.foreach { case (item, _) => {
        if (item._1 == "ex") {
          rml = "@base <" + item._2 + ">.\n" + rml
        } else {
          rml = "@prefix " + item._1 + ": <" + item._2 + ">.\n" + rml
        }
      }
      }
    })

    database.rml_text = rml
    Ok(Json.toJson(Json.obj(
      "csv_file_path" -> database.csv_file_path,
      "rml_text" -> database.rml_text,
      "instance_name" -> database.instance_name,
      "branch_name" -> database.branch_name
    )))
  }
}
