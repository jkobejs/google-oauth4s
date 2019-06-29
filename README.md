# Google Oauth 2.0 for Scala

Effectfull API for [Google OAuth 2.0][google-oauth] flows.

Installation
------------
Publish library locally and then include following line in your `build.sbt`

```scala
libraryDependencies += "com.jkobejs" %% "google-oauth4s" % "0.0.1-SNAPSHOT"
```

Server to server
----------------

To support server-to-server interactions, first create a service account for your project in the Google API Console.

`google-oauth4s` offers method for one time authorization or methods for subsequent authorization over time.

### Example

```scala
import java.time.Instant

import cats.effect._
import cats.syntax.all._
import com.jkobejs.cats.google.oauth4s.ServerToServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main extends IOApp {
  val privateKey  = "sample-private-key" // read it in a safe way
  val projectId   = "sample-project"
  val clientEmail = "sample@email.iam.gserviceaccount.com"
  val url         = "https://www.googleapis.com/oauth2/v4/token"
  val scope       = "https://www.googleapis.com/auth/devstorage.read_write"

  val claims = ServerToServer.GoogleClaims(
    issuer = clientEmail,
    scope = scope,
    audience = url,
    expiration = Instant.now().plusSeconds(3600),
    issuedAt = Instant.now()
  )

  val settings = ServerToServer.Settings(
    uri = "https://www.googleapis.com/oauth2/v4/token",
    privateKey = privateKey,
    grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer",
    claims = claims
  )

  def authOnceExample: IO[Unit] =
    ServerToServer.auth[IO](settings, global).map(println(_))

  def resourceExample: IO[Unit] =
    ServerToServer
      .resource[IO](settings, global)
      .use { authenticator =>
        for {
          authResponse1 <- authenticator.auth
          _             <- IO.sleep(2.seconds)
          authResponse2 <- authenticator.auth
        } yield s"$authResponse1\n$authResponse2"
      }
      .map(println(_))

  def streamExample: IO[Unit] =
    fs2.Stream
      .awakeEvery[IO](3.seconds)
      .zip(ServerToServer.stream[IO](settings, global))
      .map(_._2)
      .take(5)
      .compile
      .toVector
      .map(_.mkString("\n"))
      .map(println(_))

  def program: IO[Unit] =
    for {
      _ <- authOnceExample
      _ <- resourceExample
      _ <- streamExample
    } yield ()

  def run(args: List[String]): IO[ExitCode] =
    program.as(ExitCode.Success)
}
```

[google-oauth]: https://developers.google.com/identity/protocols/OAuth2
