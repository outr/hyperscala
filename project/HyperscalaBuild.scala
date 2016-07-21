import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import scoverage.ScoverageSbtPlugin.autoImport._

object HyperscalaBuild extends Build {
  lazy val root = project.in(file("."))
    .aggregate(client, server)
    .settings(sharedSettings())
    .settings(publishArtifact := false)

  lazy val core = crossProject.crossType(HyperscalaCrossType).in(file("core"))
    .settings(sharedSettings(Some("core")): _*)
    .settings(
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _),
      libraryDependencies ++= Seq(
        "com.outr.scribe" %%% "scribe" % Dependencies.scribe,
        "com.lihaoyi" %%% "upickle" % Dependencies.uPickle,
        "pl.metastack" %%% "metarx" % Dependencies.metaRx,
        "org.scalatest" %%% "scalatest" % Dependencies.scalaTest % "test"
      ),
      autoAPIMappings := true,
      apiMappings += (scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"))
    )
    .jsSettings(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % Dependencies.scalaJSDOM
      ),
      coverageEnabled := false,
      scalaJSStage in Global := FastOptStage
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "com.outr.scribe" %% "scribe-slf4j" % Dependencies.scribe,
        "io.undertow" % "undertow-core" % Dependencies.undertow,
        "org.powerscala" %% "powerscala-io" % Dependencies.powerscala
      ),
      coverageEnabled in Test := true
    )
  lazy val client = core.js
  lazy val server = core.jvm

  lazy val example = crossProject.crossType(HyperscalaCrossType).in(file("example"))
    .settings(sharedSettings(Some("example")): _*)
    .settings(
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _),
      autoAPIMappings := true,
      apiMappings += (scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"))
    )
    .jsSettings(
      coverageEnabled := false,
      scalaJSStage in Global := FastOptStage,
      crossTarget in fastOptJS := baseDirectory.value / ".." / "jvm" / "src" / "main" / "web" / "app",
      crossTarget in fullOptJS := baseDirectory.value / ".." / "jvm" / "src" / "main" / "web" / "app"
    )
    .jvmSettings(
      coverageEnabled in Test := true
    )
  lazy val exampleJS = example.js.dependsOn(client)
  lazy val exampleJVM = example.jvm.dependsOn(server)

  private def sharedSettings(projectName: Option[String] = None) = Seq(
    name := s"${Details.name}${projectName.map(pn => s"-$pn").getOrElse("")}",
    version := Details.version,
    organization := Details.organization,
    scalaVersion := Details.scalaVersion,
    sbtVersion := Details.sbtVersion,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases"),
      Resolver.typesafeRepo("releases")
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    publishTo <<= version {
      (v: String) =>
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT")) {
          Some("snapshots" at nexus + "content/repositories/snapshots")
        } else {
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
        }
    },
    publishArtifact in Test := false,
    pomExtra := <url>${Details.url}</url>
      <licenses>
        <license>
          <name>{Details.licenseType}</name>
          <url>{Details.licenseURL}</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <developerConnection>scm:{Details.repoURL}</developerConnection>
        <connection>scm:{Details.repoURL}</connection>
        <url>{Details.projectURL}</url>
      </scm>
      <developers>
        <developer>
          <id>{Details.developerId}</id>
          <name>{Details.developerName}</name>
          <url>{Details.developerURL}</url>
        </developer>
      </developers>
  )
}

object Details {
  val organization = "org.hyperscala"
  val name = "hyperscala"
  val version = "2.0.0-SNAPSHOT"
  val url = "http://hyperscala.org"
  val licenseType = "MIT"
  val licenseURL = "http://opensource.org/licenses/MIT"
  val projectURL = "https://github.com/outr/hyperscala"
  val repoURL = "https://github.com/outr/hyperscala.git"
  val developerId = "darkfrog"
  val developerName = "Matt Hicks"
  val developerURL = "http://matthicks.com"

  val sbtVersion = "0.13.11"
  val scalaVersions = List("2.12.0-M4", "2.11.8")
  val scalaVersion = "2.11.8"
}

object Dependencies {
  val scribe = "1.2.3"
  val undertow = "1.4.0.CR3"
  val uPickle = "0.4.1"
  val scalaTest = "3.0.0-M16-SNAP4"
  val metaRx = "0.1.7"
  val powerscala = "2.0.2"
  val scalaJSDOM = "0.9.1"
}

object HyperscalaCrossType extends org.scalajs.sbtplugin.cross.CrossType {
  override def projectDir(crossBase: File, projectType: String): File = crossBase / projectType

  override def sharedSrcDir(projectBase: File, conf: String): Option[File] = Some(projectBase.getParentFile / "src" / conf / "scala")
}