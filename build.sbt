import microsites._

name := "google-oauth4s"
version := "0.0.1-SNAPSHOT"

lazy val scala212               = "2.12.8"
lazy val scala211               = "2.11.12"
lazy val supportedScalaVersions = List(scala212, scala211)

scalaVersion := scala212
crossScalaVersions := supportedScalaVersions

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                            // Specify character encoding used by source files.
  "-explaintypes",                    // Explain type errors in more detail.
  "-feature",                         // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",           // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",    // Allow macro definition (besides implementation and application)
  "-language:higherKinds",            // Allow higher-kinded types
  "-language:implicitConversions",    // Allow definition of implicit functions called views
  "-unchecked",                       // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                      // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                 // Fail the compilation if there are any warnings.
  "-Xfuture",                         // Turn on future language features.
  "-Xlint:adapted-args",              // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:delayedinit-select",        // Selecting member of DelayedInit.
  "-Xlint:doc-detached",              // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",              // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",      // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",          // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",              // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",           // Option.apply used implicit view.
  "-Xlint:package-object-classes",    // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",    // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",            // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",               // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",     // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",             // Pattern match may not be typesafe.
  "-Yno-adapted-args",                // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",            // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                 // Warn when dead code is identified.
  "-Ywarn-inaccessible",              // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",          // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",              // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",             // Warn when numerics are widened.
  "-Ywarn-value-discard"              // Warn when non-Unit expression results are unused.
) ++
  (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) =>
      Seq(
        "-Ywarn-unused-import" // Warn if an import selector is not referenced.
      )
    case _ =>
      Seq(
        "-Ywarn-extra-implicit",   // Warn when more than one implicit parameter section is defined.
        "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
        "-Ywarn-unused:locals",    // Warn if a local definition is unused.
        "-Ywarn-unused:params",    // Warn if a value parameter is unused.
        "-Ywarn-unused:patvars",   // Warn if a variable bound in a pattern is unused.
        "-Ywarn-unused:privates",  // Warn if a private member is unused.
        "-Ywarn-unused:imports",   // Warn if an import selector is not referenced.
        "-Xlint:constant"          // Evaluation of a constant arithmetic expression results in an error.
      )
  })
scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")
scalacOptions in Tut --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

val tsec = "0.1.0"

lazy val library =
  new {
    object Version {
      val tsec      = "0.1.0"
      val http4s    = "0.20.3"
      val scalatest = "3.0.7"
      val wiremock  = "2.18.0"
    }

    val tsecCommon        = "io.github.jmcardon"     %% "tsec-common"         % Version.tsec
    val tsecJWTSig        = "io.github.jmcardon"     %% "tsec-jwt-sig"        % Version.tsec
    val http4sBlazeClient = "org.http4s"             %% "http4s-blaze-client" % Version.http4s
    val http4sCirce       = "org.http4s"             %% "http4s-circe"        % Version.http4s
    val scalaTest         = "org.scalatest"          %% "scalatest"           % Version.scalatest
    val wiremock          = "com.github.tomakehurst" % "wiremock"             % Version.wiremock

  }

libraryDependencies ++= Seq(
  library.tsecCommon,
  library.tsecJWTSig,
  library.http4sBlazeClient,
  library.http4sCirce,
  library.scalaTest % Test,
  library.wiremock  % Test
)

enablePlugins(MicrositesPlugin)
micrositeTwitterCreator := "@jkobejs"
micrositeConfigYaml := ConfigYml(
  yamlCustomProperties = Map(
    "tsecVersion"          -> library.Version.tsec,
    "http4sVersion"        -> library.Version.http4s,
    "googleOauth4sVersion" -> version.value
  )
)
micrositeAuthor := "Josip Grgurica"
micrositeCompilingDocsTool := WithMdoc
micrositeGithubOwner := "jkobejs"
micrositeGithubRepo := "google-oauth4s"
micrositePushSiteWith := GitHub4s
micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
micrositeBaseUrl := "google-oauth4s"
includeFilter in Jekyll := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.md"
