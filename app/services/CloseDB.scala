package services

import org.dizitart.no2.Nitrite
import org.slf4j.{Logger, LoggerFactory}
import play.api.inject.ApplicationLifecycle
import javax.inject._
import scala.concurrent.Future

@Singleton
class CloseDB @Inject() (database: MappingsDB, appLifecycle: ApplicationLifecycle) {
  val logger:Logger = LoggerFactory.getLogger(this.getClass)
  appLifecycle.addStopHook { () =>
    val db : Nitrite = database.get_db
    database.get_mongo_client.close()
    logger.info(s"CloseDB: closing database connection.")
    if (!db.isClosed)
      db.close()
    Future.successful(None)
  }
}
