package io.github.jkobejs.google.oauth4s

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, containing, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class ServerToServerSuite extends FunSuite with BeforeAndAfterAll {
  private val privateKey =
    "MIIBOgIBAAJBAJHPYfmEpShPxAGP12oyPg0CiL1zmd2V84K5dgzhR9TFpkAp2kl2" +
      "9BTc8jbAY0dQW4Zux+hyKxd6uANBKHOWacUCAwEAAQJAQVyXbMS7TGDFWnXieKZh" +
      "Dm/uYA6sEJqheB4u/wMVshjcQdHbi6Rr0kv7dCLbJz2v9bVmFu5i8aFnJy1MJOpA" +
      "2QIhAPyEAaVfDqJGjVfryZDCaxrsREmdKDlmIppFy78/d8DHAiEAk9JyTHcapckD" +
      "uSyaE6EaqKKfyRwSfUGO1VJXmPjPDRMCIF9N900SDnTiye/4FxBiwIfdynw6K3dW" +
      "fBLb6uVYr/r7AiBUu/p26IMm6y4uNGnxvJSqe+X6AxR6Jl043OWHs4AEbwIhANuz" +
      "Ay3MKOeoVbx0L+ruVRY5fkW+oLHbMGtQ9dZq7Dp9"

  private val grant = "grant"

  private def getSettings: ServerToServer.Settings = ServerToServer.Settings(
    uri = s"http://localhost:${server.port()}/oauth2/v4/token",
    privateKey = privateKey,
    grantType = grant,
    claims = ServerToServer.GoogleClaims(
      issuer = "clientEmail",
      scope = "scope",
      audience = "url",
      expiration = Instant.now().plusSeconds(3600),
      issuedAt = Instant.now()
    )
  )

  implicit val ctx   = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  private val server = new WireMockServer(wireMockConfig().dynamicPort().dynamicHttpsPort())

  override def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  private def mockTokenApi(accessToken: String, expiresIn: Long): Unit = {
    val mock = new WireMock("localhost", server.port())

    mock.register(
      WireMock
        .post(
          urlEqualTo("/oauth2/v4/token")
        )
        .withRequestBody(containing(s"grant_type=grant&assertion="))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{"access_token": "$accessToken", "token_type": "Bearer", "expires_in": $expiresIn}"""
            )
            .withHeader("Content-Type", "application/json")
        )
    )

    ()
  }

  test(".auth auths server once") {
    val accessToken = "accessToken"
    val expiresIn   = 60L
    mockTokenApi(accessToken, expiresIn)

    val settings = getSettings

    val authResponse = ServerToServer.auth[IO](settings, global).unsafeRunSync

    assertResult(
      ServerToServer.AuthResponse(
        access_token = accessToken,
        token_type = "Bearer",
        expires_in = expiresIn
      )
    )(authResponse)
  }

  test(".resource provides resource that exposes authenticator for multiple auths") {
    val accessToken = "accessToken"
    val expiresIn   = 60L
    mockTokenApi(accessToken, expiresIn)

    val settings     = getSettings
    val authResponse = ServerToServer.resource[IO](settings, global).use(_.auth).unsafeRunSync()

    assertResult(
      ServerToServer.AuthResponse(
        access_token = accessToken,
        token_type = "Bearer",
        expires_in = expiresIn
      )
    )(authResponse)
  }

  test(".stream streams auth responses with new responses after token expires") {
    val accessToken = "accessToken"
    val expiresIn   = 63L
    mockTokenApi(accessToken, expiresIn)

    val settings = getSettings

    val tokenUpdatesIO =
      fs2.Stream
        .awakeEvery[IO](1.5.seconds)
        .map(seconds => mockTokenApi(accessToken + seconds.toSeconds, expiresIn))
        .take(6)
        .compile
        .toVector

    val authResponsesIO =
      fs2.Stream
        .awakeEvery[IO](2.seconds)
        .zip(ServerToServer.stream[IO](settings, global))
        .map(_._2)
        .take(3)
        .compile
        .toVector

    val authResponses = (tokenUpdatesIO, authResponsesIO)
      .parMapN { (_, responses) =>
        responses
      }
      .unsafeRunSync()

    val expectedAuthResponses = Vector(
      ServerToServer.AuthResponse(
        access_token = accessToken + 1,
        token_type = "Bearer",
        expires_in = expiresIn
      ),
      ServerToServer.AuthResponse(
        access_token = accessToken + 1,
        token_type = "Bearer",
        expires_in = expiresIn - 2
      ),
      ServerToServer.AuthResponse(
        access_token = accessToken + 4,
        token_type = "Bearer",
        expires_in = expiresIn
      )
    )

    assertResult(
      expectedAuthResponses
    )(authResponses)
  }
}
