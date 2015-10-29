name := "bark"

organization := "io.github.davepkennedy"

version := "0.1"

scalaVersion := "2.11.7"

scalacOptions += "-target:jvm-1.8"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.0" % "test",
  "com.googlecode.lanterna" % "lanterna" % "2.1.9",
  "com.typesafe.akka" %% "akka-actor" % "2.4.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.12"
)
