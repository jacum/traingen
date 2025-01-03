import WartRemoverSettings.wartExclusionsMain
import WartRemoverSettings.wartExclusionsTest

ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.4"

val Http4sVersion = "0.23.30"

lazy val root = project.in(file(".")).aggregate(service)
  .settings(name := "training-generator-root")

lazy val service = (project in file("service"))
  .enablePlugins(WartRemover, GuardrailPlugin, JavaAppPackaging, DockerPlugin)
  .settings(
    dockerExposedPorts ++= Seq(8080),
    dockerBaseImage := "ubi9/openjdk-21",
    Compile / compile / wartremoverErrors ++= Warts.allBut(wartExclusionsMain *),
    Compile / test / wartremoverErrors ++= Warts.allBut(wartExclusionsTest *),
    Compile / scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-language:postfixOps",
      s"-P:wartremover:excluded:${sourceManaged.value.asFile.getPath}",
    ),
    doc / sources := Seq(),
    packageDoc / publishArtifact := false,
    packageSrc / publishArtifact := false,
    ThisBuild / parallelExecution := false,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.http4s" %% "http4s-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "io.circe" %% "circe-parser" % "0.14.9",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0", // For using SLF4J backend
      "org.slf4j" % "slf4j-simple" % "2.0.9", // Simple SLF4J implementation
      "org.scalatest" %% "scalatest" % "3.2.16" % Test,
    ),
    Compile / guardrailTasks := List(/*ScalaServer(
      file("openapi/traingen.yaml"),
      framework = "http4s",
      pkg = "nl.pragmasoft.traingen.http",
      imports = List("nl.pragmasoft.traingen.Codecs.given"),
      encodeOptionalAs = codingOptional,
      decodeOptionalAs = codingOptional,
    ),*/
      ScalaServer(
        file("openapi/user-api.yaml"),
        framework = "http4s",
        pkg = "nl.pragmasoft.traingen.http",
        imports = List("nl.pragmasoft.traingen.Codecs.given"),
        encodeOptionalAs = codingOptional,
        decodeOptionalAs = codingOptional,
      )),
    name := "training-generator-service"
  )
