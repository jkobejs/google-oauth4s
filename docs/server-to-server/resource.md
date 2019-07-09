---
layout: docs
title: Resource
---

### Resource example

First we need to import everthing that we need.

```scala mdoc
import java.time.Instant
import cats.effect._
import io.github.jkobejs.google.oauth4s.ServerToServer
import io.github.jkobejs.google.oauth4s.ServiceAccountKeyReader
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
```

We will use `cats.effect.IO` effect wrapper to make our computation pure. To be able to use it we need to create context shift and timer which are need for shifting execution and scheduling of tasks.

```scala mdoc
implicit val ctx = IO.contextShift(global)
implicit val timer = IO.timer(global)
```

To communicate with google auth api we need to create claims and settings. We can do it in two ways, create them manually (private key should we read in a safe way) or read service account key data from google service account key file and use it to create settings.

Let's first do it manually.

```scala mdoc
{
  val privateKey = "sample-private-key" // read it in a safe way
  val clientEmail = "sample@email.iam.gserviceaccount.com"
  val url = "https://www.googleapis.com/oauth2/v4/token"
  val scope = "https://www.googleapis.com/auth/devstorage.read_write"
  
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

  ServerToServer
      .resource[IO](settings, global)
      .use { authenticator =>
        for {
          authResponse1 <- authenticator.auth
          _             <- IO.sleep(2.seconds)
          authResponse2 <- authenticator.auth
        } yield List(authResponse1, authResponse2)
      }
}
```

Now lets see how to use `ServiceAccountKey`.

```scala mdoc
val authenticatorResource = for {
  serviceAccountKey <- Resource.liftF(ServiceAccountKeyReader.readServiceAccountKey[IO]("src/test/resources/service-account.json", global))
  scope = "https://www.googleapis.com/auth/devstorage.read_write"
  claims = ServerToServer.GoogleClaims(
    issuer = serviceAccountKey.client_email,
    scope = scope,
    audience = serviceAccountKey.token_uri,
    expiration = Instant.now().plusSeconds(3600),
    issuedAt = Instant.now()
  )
  settings =  ServerToServer.Settings(
    uri = serviceAccountKey.token_uri,
    privateKey = serviceAccountKey.private_key,
    grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer",
    claims = claims
  )
 resource <- ServerToServer.resource[IO](settings, global)
} yield resource

authenticatorResource.use { authenticator =>
  for {
    authResponse1 <- authenticator.auth
    _             <- IO.sleep(2.seconds)
    authResponse2 <- authenticator.auth
  } yield List(authResponse1, authResponse2)
}
```
