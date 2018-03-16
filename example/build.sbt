lazy val root = (project in file("."))
  .settings(
    name := "kamon-stackdriver-sample",
    scalaVersion := "2.11.11",
    libraryDependencies ++= Seq(
      "com.github.uryyyyyyy" %% "kamon-stackdriver" % "0.2.1-SNAPSHOT",
      "io.kamon" %% "kamon-system-metrics" % "1.0.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
    //resolvers += "Local Maven Repository" at s"file:/${System.getProperty("user.home")}/Desktop/"
  ).enablePlugins(JavaAppPackaging)
