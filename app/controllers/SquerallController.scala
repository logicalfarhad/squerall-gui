package controllers

import com.datastax.driver.core.Cluster
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import java.io.{BufferedReader, InputStreamReader, _}
import java.net.URI
import javax.inject._
import scala.collection.immutable.HashMap
import scala.io.{BufferedSource, Source}
import scala.sys.process._


@Singleton
class SquerallController @Inject()(cc: ControllerComponents, playconfiguration: Configuration) extends AbstractController(cc) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)


  def index: Action[AnyContent] = Action {
    Ok(views.html.squerall("Home", null))
  }

  def query: Action[AnyContent] = Action {
    Ok(views.html.squerall("Query", null))
  }

  def addSource(): Action[AnyContent] = Action {
    Ok(views.html.squerall("Add source", null))
  }

  def addMappings(): Action[AnyContent] = Action {
    val configs = Source.fromFile("config")
    val data: String = try configs.mkString finally configs.close()
    var source_types: Map[String, String] = Map()
    if (data.trim != "") {
      val json: JsValue = Json.parse(data)
      case class SourceObject(dtype: String, entity: String)

      implicit val userReads: Reads[SourceObject] = (
        (__ \ Symbol("type")).read[String] and
          (__ \ Symbol("entity")).read[String]
        ) (SourceObject)

      val sources = (json \ "sources").as[Seq[SourceObject]]

      for (s <- sources) {
        source_types = source_types + (s.entity -> s.dtype)
      }
    }

    Ok(views.html.squerall("Add mappings", source_types))
  }

  def annotate(entity: String): Action[AnyContent] = Action {
    import scala.language.postfixOps
    val configs: BufferedSource = Source.fromFile("config")
    val data: String = try configs.mkString finally configs.close()
    val json: JsValue = Json.parse(data)

    case class ConfigObject(dtype: String, source: String, options: Map[String, String], entity: String)

    implicit val userReads: Reads[ConfigObject] = (
      (__ \ Symbol("type")).read[String] and
        (__ \ Symbol("source")).read[String] and
        (__ \ Symbol("options")).read[Map[String, String]] and
        (__ \ Symbol("entity")).read[String]
      ) (ConfigObject)

    var optionsPerStar: HashMap[String, Map[String, String]] = HashMap()

    val sources = (json \ "sources").as[Seq[ConfigObject]]

    var source = ""
    var options: Map[String, String] = Map()
    var dtype = ""

    for (s <- sources) {
      if (s.entity == entity) {
        source = s.source
        options = s.options
        dtype = s.dtype
        optionsPerStar += (source -> options)
      }
    }

    var schema = ""
    var parquet_schema = ""

    if (dtype == "csv") {
      if (source.contains("hdfs://")) {
        import org.apache.hadoop.conf.Configuration
        import org.apache.hadoop.fs.Path
        import org.apache.hadoop.hdfs.DistributedFileSystem

        val fileSystem = new DistributedFileSystem()
        val conf = new Configuration()
        fileSystem.initialize(new URI("hdfs://namenode-host:54310"), conf)
        val input = fileSystem.open(new Path(source))
        schema = new BufferedReader(new InputStreamReader(input)).readLine()

      } else {
        val f = new File(source)
        schema = firstLine(f).get // in theory, we always have a header
      }

    } else if (dtype == "parquet") {
      val pathToParquetToolsJar = playconfiguration.underlying.getString("pathToParquetToolsJar")
      parquet_schema = "java -jar " + pathToParquetToolsJar + " schema " + source !!

      parquet_schema = parquet_schema.substring(parquet_schema.indexOf('\n') + 1)

      val set = parquet_schema.split("\n").toSeq.map(_.trim).filter(_ != "}").map(f => f.split(" ")(2))

      for (s <- set) {
        schema = schema + "," + s.replace(";", "")
      } // weirdly, there was a ; added from nowhere

      schema = schema.substring(1)
    } else if (dtype == "cassandra") {

      val table = optionsPerStar(source)("table")
      val keyspace = optionsPerStar(source)("keyspace")

      var cluster: Cluster = null
      try {
        cluster = com.datastax.driver.core.Cluster.builder()
          .addContactPoint("127.0.0.1")
          .build()

        val session: com.datastax.driver.core.Session = cluster.connect()

        val rs: com.datastax.driver.core.ResultSet = session.execute("select column_name from system_schema.columns where keyspace_name = '" + keyspace + "' and table_name ='" + table + "'")
        val it = rs.iterator()
        while (it.hasNext) {
          val row = it.next()
          schema = schema + row.getString("column_name") + ","
        }
      } finally {
        if (cluster != null) cluster.close()
      }
    } else if (dtype == "mongodb") {
      import com.mongodb.MongoClient
      import scala.jdk.CollectionConverters._

      val url = optionsPerStar(source)("url")
      val db = optionsPerStar(source)("database")
      val col = optionsPerStar(source)("collection")

      val mongoClient = new MongoClient(url)
      val database = mongoClient.getDatabase(db)
      val collection = database.getCollection(col)

      var set = Set[String]()
      for (cur <- collection.find.limit(100).asScala) {
        for (x <- cur.asScala) {
          set = set + x._1
        }
      }

      schema = set.mkString(",").replace("_id,", "")
      mongoClient.close()
    } else if (dtype == "jdbc") { // TODO: specify later MySQL, SQL Server, etc.
      import java.sql.{Connection, DriverManager}

      val driver = optionsPerStar(source)("driver")
      val url = optionsPerStar(source)("url")
      val username = optionsPerStar(source)("user")
      val password = optionsPerStar(source)("password")
      val dbtable = optionsPerStar(source)("dbtable")
      var connection: Connection = null

      try {
        // make the connection
        Class.forName(driver)
        connection = DriverManager.getConnection(url, username, password)

        // create the statement, and run the select query
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery("SHOW COLUMNS FROM " + dbtable)
        while (resultSet.next()) {
          val field = resultSet.getString("Field")
          schema = schema + field + ","
        }
      } catch {
        case e: Throwable => e.printStackTrace()
      }

      connection.close()
      schema = omitLastChar(schema)
    }

    Ok(views.html.squerall1("Annotate source", source, options, dtype, schema, entity))
  }

  // helping methods
  def firstLine(f: File): Option[String] = {
    val src = Source.fromFile(f)
    try {
      src.getLines().find(_ => true)
    } finally {
      src.close()
    }
  }

  def omitLastChar(str: String): String = {
    var s = ""
    if (str != null && str.nonEmpty) {
      s = str.substring(0, str.length() - 1)
    }
    s
  }
}
