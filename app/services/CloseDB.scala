package services

import org.dizitart.no2.Nitrite
import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle

import javax.inject._
import scala.concurrent.Future

@Singleton
class CloseDB @Inject() (database: MappingsDB, appLifecycle: ApplicationLifecycle) {
  val logger = LoggerFactory.getLogger(this.getClass)
  appLifecycle.addStopHook { () =>
    val db : Nitrite = database.getDB
    logger.info(s"CloseDB: closing database connection.")
    if (!db.isClosed)
      db.close()
    Future.successful(None)
  }
}
