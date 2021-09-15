package services
import org.slf4j.{Logger, LoggerFactory}
import play.api.inject.ApplicationLifecycle
import javax.inject._
import scala.concurrent.Future

@Singleton
class CloseDB @Inject() (database: MappingsDB, appLifecycle: ApplicationLifecycle) {
  val logger:Logger = LoggerFactory.getLogger(this.getClass)
  appLifecycle.addStopHook { () =>
    database.get_mongo_client.close()
    logger.info(s"CloseDB: closing database connection.")

    Future.successful(None)
  }
}
