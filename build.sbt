lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(
  name := """Squerall-GUI""",
  organization := "com.fraunhofer.vocoreg",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.13.5",
  libraryDependencies ++= Seq(
    guice,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
    "org.dizitart" % "nitrite" % "3.0.2",
    "org.apache.hadoop" % "hadoop-common" % "3.2.0",
    "org.apache.hadoop" % "hadoop-core" % "1.2.0",
    "com.outworkers" %% "phantom-dsl" % "2.59.0",
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.3.1",
    "org.mongodb" % "mongo-java-driver" % "3.10.2",
    "mysql" % "mysql-connector-java" % "8.0.15",
    "org.apache.jena" % "jena-core" % "3.10.0",
    "org.apache.jena" % "jena-arq" % "3.10.0"
  )
)




