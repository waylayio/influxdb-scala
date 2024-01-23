resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")

addSbtPlugin("com.github.sbt" % "sbt-ghpages" % "0.7.0")

addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.2.1")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.5.2")
