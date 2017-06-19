val kamonCore = "io.kamon" %% "kamon-core" % "0.6.7"
val monitoring = "com.google.apis" % "google-api-services-monitoring" % "v3-rev406-1.22.0"

lazy val root = (project in file("."))
  .settings(name := "kamon-stackdriver")
  .settings(
    scalaVersion := "2.11.11",
    publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/Desktop/my-repository"))),
    libraryDependencies ++=
      compileScope(kamonCore, monitoring, akkaDependency("actor").value, scalaCompact.value) ++
        testScope(scalatest, akkaDependency("testkit").value, slf4jApi, slf4jnop))



def scalaCompact = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10" | "2.11" => "org.scala-lang.modules" %% "scala-java8-compat" % "0.5.0"
    case "2.12" => "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
  }
}
