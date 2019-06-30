---
layout: home
title: "Home"
section: "home"
---

# Google Oauth 2.0 for Scala

Effectfull API for [Google OAuth 2.0][google-oauth] flows for Scala which is constrained by cats effect **Sync** typeclass. Currently it supports only server to server interaction.

Quick start
------------
The current version is **{{site.googleOauth4sVersion}}** for **Scala 2.11/12** with
- [tsec][tsec] {{site.tsecVersion}}
- [http4s][http4s] {{site.http4sVersion}}

To use library publish it locally.
```scala
scalacOptions += "-Ypartial-unification" // 2.11.9+

libraryDependencies += "com.jkobejs" %% "google-oauth4s" % "{{site.googleOauth4sVersion}}"
```

Server to server
----------------

To support server-to-server interactions, first create a service account for your project in the Google API Console.

`google-oauth4s` offers methods for one time authorization or subsequent authorizations over time.

### Usage

```scala mdoc
import java.time.Instant

import cats.effect._
import cats.syntax.all._
import com.jkobejs.google.oauth4s.ServerToServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main extends IOApp {
  val privateKey  = "sample-private-key" // read it in a safe way
  val projaectId   = "sample-project"
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
[tsec]: https://jmcardon.github.io/tsec/
[http4s]: https://http4s.org/
