resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("com.github.sbt" % "sbt-site"       % "1.6.0")
addSbtPlugin("com.github.sbt" % "sbt-release"    % "1.4.0")
addSbtPlugin("com.github.sbt" % "sbt-ghpages"    % "0.7.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.1")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"   % "2.5.5")
