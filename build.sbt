lazy val root = (project in file("."))
  .settings(
    name := "kamon-stackdriver",
    organization := "com.github.uryyyyyyy",
    version := "0.3.1",
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
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12", "2.12.6"),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % "1.1.3",
      "com.google.cloud" % "google-cloud-monitoring" % "1.45.0"
    )
    //publishTo := Some(Resolver.file("file",  new File(s"${System.getProperty("user.home")}/Desktop/")) )
  )
