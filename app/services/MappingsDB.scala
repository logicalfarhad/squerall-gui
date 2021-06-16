package services

import org.dizitart.no2.{Cursor, Filter, Nitrite, NitriteCollection}
import org.mongodb.scala.bson.BsonValue
import org.mongodb.scala.{Document, MongoClient, MongoCollection, bson}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import java.io.File
import javax.inject._
import scala.io.Source

trait MappingsDB {

  var csv_file_path: String = ""
  var rml_text: String = ""
  var branch_name: String = ""
  var instance_name: String = ""

  def firstLine(source: String): Option[String]

  def get_db: Nitrite

  def connect_db: Nitrite

  def get_mongo_client: MongoClient

  def get_cursor(name: String, filter: Filter): Cursor

  def get_mongo_db_collection(name: String): MongoCollection[Document]

  def get_collection(name: String): NitriteCollection

  def getMapFromOption(name: Option[BsonValue]): Map[String, String]
}

@Singleton
class MappingsDBInstance @Inject()(configuration: Configuration) extends MappingsDB {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  logger.info(s"MappingsDB: Starting a database connection.")
  var db: Nitrite = _
  override def get_db: Nitrite = {
    db
  }

  override def get_cursor(name: String, filter: Filter): Cursor = {
    if (filter == null)
      get_collection(name).find
    else
      get_collection(name).find(filter)
  }

  override def get_collection(name: String): NitriteCollection = {
    connect_db.getCollection(name)
  }

  override def connect_db: Nitrite = {
    if (db == null) {
      val sourcesConfFile: String = configuration.underlying.getString("mappingsDB")
      db = Nitrite.builder()
        .filePath(sourcesConfFile)
        .openOrCreate()
    }
    db
  }

  override def get_mongo_db_collection(name: String): MongoCollection[Document] = {
    val mongodb = get_mongo_client.getDatabase("vocoreg")
    mongodb.getCollection(name)
  }

  override def get_mongo_client: MongoClient = {
    MongoClient("mongodb://127.0.0.1:27017")
  }

  override def getMapFromOption(name:Option[bson.BsonValue]):Map[String,String] ={
    var optionMap: Map[String, String] = Map()
    name match {
      case Some(s) => s.asDocument().entrySet().forEach(x => {
        val opt_key = x.getKey
        val opt_val = x.getValue.asString().getValue
        optionMap += (opt_key -> opt_val)
      })case None=>println("Did not find anything")
    }
    optionMap
  }


  override def firstLine(fileSource: String): Option[String] = {
    val src = Source.fromFile(new File(fileSource))
    try {
      src.getLines().find(_ => true)
    } finally {
      src.close()
    }
  }
}
