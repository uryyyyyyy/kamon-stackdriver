val akkaActor = "com.typesafe.akka" %% s"akka-actor" % "2.3.15"

lazy val root = (project in file("."))
  .settings(
    name := "kamon-stackdriver",
    organization := "com.github.uryyyyyyy",
    version := "0.0.2"
  )
  .settings(
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/uryyyyyyy/kamon-stackdriver")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/uryyyyyyy/kamon-stackdriver"),
        "scm:git@github.com:uryyyyyyy/kamon-stackdriver.git"
      )
    ),
    developers := List(
      Developer(
        id = "uryyyyyyy",
        name = "Koki Shibata",
        email = "koki305@gmail.com",
        url = url("https://github.com/uryyyyyyy")
      )
    ),
    publishMavenStyle := true,
    scalaVersion := "2.11.11",
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % "0.6.7",
      "com.google.apis" % "google-api-services-monitoring" % "v3-rev406-1.22.0"
    )
  )
