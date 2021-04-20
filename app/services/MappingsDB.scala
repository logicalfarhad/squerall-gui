package services

import org.dizitart.no2.{Cursor, Filter, Nitrite, NitriteCollection}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration

import javax.inject._

trait MappingsDB {
  def get_db: Nitrite

  def connect_db: Nitrite

  def get_cursor(name: String, filter: Filter): Cursor

  def get_collection(name: String): NitriteCollection
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
}
