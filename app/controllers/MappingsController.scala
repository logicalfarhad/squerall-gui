package controllers

import org.dizitart.no2.{Document, NitriteCollection}
import org.slf4j.{Logger, LoggerFactory}
import play.api.mvc._
import services.MappingsDB
import javax.inject._


@Singleton
class MappingsController @Inject()(cc: ControllerComponents, database: MappingsDB) extends AbstractController(cc) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def insert: Action[AnyContent] = Action(parse.tolerantFormUrlEncoded) {
    val collection: NitriteCollection = database.get_collection("mappings")

    val doc = Document.createDocument("entity", 4)
                      .put("source", 1)
                      .put("ID", 2)
                      .put("class", 3)
    collection.insert(doc)
    Ok("Document added")
  }
}
