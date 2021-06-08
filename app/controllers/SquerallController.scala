package controllers

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.{Document, MongoCollection}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import play.api.mvc._
import services.Helpers.GenericObservable
import services.MappingsDB
import java.io._
import java.util.UUID
import javax.inject._
import scala.concurrent.Future
import scala.io.Source

@Singleton
class SquerallController @Inject()(cc: ControllerComponents, database: MappingsDB) extends AbstractController(cc) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val mongoCollection: MongoCollection[Document] = database.get_mongo_db_collection("mappingcollection")

  def index: Action[AnyContent] = Action {
    Ok(views.html.squerall("Home", null)).withSession("session" ->UUID.randomUUID().toString)
  }

  def getAll(branchname: String, instanceName: String): Action[AnyContent] = Action {
    database.branch_name = branchname
    database.instance_name = instanceName
    Ok(Json.toJson(branchname + ":" + instanceName))
  }

  def query: Action[AnyContent] = Action {
    Ok(views.html.squerall("Query", null))
  }

  def addSource(): Action[AnyContent] = Action{ implicit request=>
    val session = request.session.get("session")
    if(session.isDefined){
     Ok(views.html.squerall("Add source", null))
    }else{
      Ok(views.html.squerall("Session Timeout", null))
    }
  }

  def addMappings(): Action[AnyContent] = Action.async { implicit request =>{
    var source_types: Map[String, String] = Map()
    val session = request.session.get("session")
    if(session.isDefined) {
      mongoCollection.find()
        .map(d => {
          val entity = d.getString("entity")
          val stype = d.getString("type")
          source_types += (entity -> stype)
        }).printResults("got documents")
      Future.successful(Ok(views.html.squerall("Add mappings", source_types)))
    }else{
      mongoCollection.drop().printResults("all documents deleted")
      Future.successful(Ok(views.html.squerall("Session Timeout", null)))
    }
  }}
  def annotate(entity: String): Action[AnyContent] = Action{ implicit request =>{
    var source = ""
    var optionList: Map[String, String] = Map()
    var dtype = ""
    var schema = ""
    var dentity = ""

    mongoCollection.find(equal("entity",entity)).first()
      .map(doc => {
        dentity = doc.getString("entity")
        if (dentity == entity) {
          dtype = doc.getString("type")
          source = doc.getString("source")
          doc.get("options") match {
            case Some(s) => s.asDocument().entrySet().forEach(x => {
              val opt_key = x.getKey
              val opt_val = x.getValue.asString().getValue
              optionList += (opt_key -> opt_val)
            })case None=>println("")
          }
          schema = firstLine(source).get
        }
      }).printHeadResult()
    Ok(views.html.squerall1("Annotate source", source, optionList, dtype, schema, entity))
  }}
  def firstLine(fileSource: String): Option[String] = {
    val src = Source.fromFile(new File(fileSource))
    try {
      src.getLines().find(_ => true)
    } finally {
      src.close()
    }
  }
}
