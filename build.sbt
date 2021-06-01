lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(
  name := """Squerall-GUI""",
  organization := "com.fraunhofer.vocoreg",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.13.6",
  libraryDependencies ++= Seq(
    guice,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
    "org.dizitart" % "nitrite" % "3.4.2",
    "org.webjars" % "bootstrap" % "5.0.0" exclude("org.webjars", "jquery"),
    "org.webjars" % "jquery" % "3.3.1-2",
    "org.webjars" % "bootstrap-datepicker" % "1.4.0" exclude("org.webjars", "bootstrap"),
    "org.webjars" % "font-awesome" % "5.15.2",
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.9.0"
  )
)
maintainer:= "SM Farhad Ali <farhad.ali@iais.fraunhofer.de>"




