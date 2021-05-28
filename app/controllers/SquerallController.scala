package controllers

import org.dizitart.no2.filters.Filters
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import services.MappingsDB
import java.io._
import javax.inject._
import scala.collection.immutable.HashMap
import scala.io.Source


@Singleton
class SquerallController @Inject()(cc: ControllerComponents, configuration: Configuration, database: MappingsDB) extends AbstractController(cc) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val sourcesConfFile: String = configuration.underlying.getString("sourcesConfFile")

  def index: Action[AnyContent] = Action {
    Ok(views.html.squerall("Home", null))
      .withSession("connected" -> "user@gmail.com")
  }

  def getAll(branchname: String, instanceName: String): Action[AnyContent] = Action {
    database.branch_name = branchname
    database.instance_name = instanceName
    Ok(Json.toJson(branchname + ":" + instanceName))
  }

  def query: Action[AnyContent] = Action {
    Ok(views.html.squerall("Query", null))
  }

  def addSource(): Action[AnyContent] = Action {
    Ok(views.html.squerall("Add source", null))
  }

  def addMappings(): Action[AnyContent] = Action {
    val collection = database.get_collection("mappings")
    val results = collection.find
    var source_types: Map[String, String] = Map()
    results.forEach(document => {
      val entity = document.get("entity").toString
      val stype = document.get("type").toString
      source_types += (entity -> stype)
    })
    Ok(views.html.squerall("Add mappings", source_types))
  }

  def annotate(entity: String): Action[AnyContent] = Action {
    var source = ""
    var options: Map[String, String] = Map()
    var dtype = ""
    var schema = ""
    val cursor = database.get_cursor("mappings", Filters.eq("entity", entity))
    var dentity = ""
    cursor.forEach(doc => {
      dentity = doc.get("entity").toString //person
      if (dentity == entity) {
        dtype = doc.get("type").toString //csv
        source = doc.get("source").toString //source/path to csv
        options = doc.get("options").asInstanceOf[HashMap[String, String]] //options
        schema = firstLine(source).get
      }
    })
    Ok(views.html.squerall1("Annotate source", source, options, dtype, schema, entity))
  }

  // helping methods
  def firstLine(fileSource: String): Option[String] = {
    val src = Source.fromFile(new File(fileSource))
    try {
      src.getLines().find(_ => true)
    } finally {
      src.close()
    }
  }

}
