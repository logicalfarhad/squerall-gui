package services

import org.dizitart.no2.Nitrite
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration

import javax.inject._

trait MappingsDB {
  def connectDB(): Nitrite

  def getDB: Nitrite
}

@Singleton
class MappingsDBInstance @Inject()(playconfiguration: Configuration) extends MappingsDB {

  val logger:Logger = LoggerFactory.getLogger(this.getClass)
  logger.info(s"MappingsDB: Starting a database connection.")
  var db: Nitrite = null

  override def connectDB(): Nitrite = {
    val mappingsDB = playconfiguration.underlying.getString("mappingsDB")
    if (db == null) {
      db = Nitrite.builder()
        .filePath(mappingsDB)
        .openOrCreate()
    }
    db
  }

  override def getDB: Nitrite = {
    db
  }
}
