
organization in ThisBuild := "be.doeraene"
version in ThisBuild := "0.1-SNAPSHOT"
scalaVersion in ThisBuild := "2.11.7"

lazy val compiler = project.
  enablePlugins(ScalaJSPlugin).
  settings(
    name := "tsc-scalajs"
  )
