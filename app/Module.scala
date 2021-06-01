import com.google.inject.AbstractModule
import services.{CloseDB, MappingsDB, MappingsDBInstance}

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MappingsDB]).to(classOf[MappingsDBInstance])
    bind(classOf[CloseDB]).asEagerSingleton()
  }
}
