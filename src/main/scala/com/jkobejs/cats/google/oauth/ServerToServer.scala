package com.jkobejs.cats.google.oauth

import java.time.Instant

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.Stream
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import tsec.common._
import tsec.jws.signature._
import tsec.jwt._
import tsec.signature.jca._

import scala.concurrent.ExecutionContext

/**
 * `ServerToServer` provides api for performing Google server-to-server OAuth 2.0 flow.
 * Underneath it communicates to google auth server using HTTP.
 *
 * @see https://developers.google.com/identity/protocols/OAuth2ServiceAccount
 */
object ServerToServer {

  /**
   * Represents settings used to connect to Google OAuth 2.0 server.
   *
   * @param uri url used for creating auth requests
   * @param privateKey private key used to sign JWT token
   * @param grantType given grant
   * @param claims claims specified by Google OAuth 2.0
   */
  final case class Settings(
    uri: String,
    privateKey: String,
    grantType: String,
    claims: GoogleClaims
  )

  /**
   * Represents the JWT Claims used in Google server-to-server oauth
   *
   * Times are IEEE Std 1003.1, 2013 Edition time in seconds. They are represented
   * in a java.time.Instant objects. At serialization time, they are
   * represented as `Long`.
   *
   * Note: When feeding `Instant` instances directly, milliseconds are discarded
   *
   * @param issuer Issuer claim, Case insensitive
   * @param scope A space-delimited list of the permissions that the application requests
   * @param audience The audience Case-sensitive. Can be either a list or a single string
   * @param expiration The token expiration time
   * @param issuedAt identifies the time at which the JWT was issued
   * @param subject Subject, Case-sensitive string when defined
   */
  final case class GoogleClaims(
    issuer: String,
    scope: String,
    audience: String,
    expiration: Instant,
    issuedAt: Instant,
    subject: Option[String] = None
  )

  /**
   * Represents Authorization Server access token response.
   *
   * Access token expires in one hour and can be reused until they expire.
   *
   * @param access_token google access token
   * @param token_type token type
   * @param expires_in when will token expire
   */
  final case class AuthResponse(
    access_token: String,
    token_type: String,
    expires_in: Long
  )

  final private case class AuthState(
    authResponse: AuthResponse,
    expiresAt: Long
  )

  /**
   * Exposes function which performs authorization and caches it until token is near to expire.
   */
  final class Authenticator[F[_]] private[oauth] (
    private val ref: Ref[F, Option[AuthState]],
    private val client: Client[F],
    private val settings: Settings
  )(implicit F: Sync[F]) {
    implicit private val authResponseEntityDecoder = jsonOf[F, AuthResponse]
    private val minute                             = 60
    private val hour                               = 3600L

    /**
     * Performs authorization on server and caches it for consequent calls.
     * If tokens is near to expire (under one minute) it will reauthorize with old settings but claim will
     * have updated expiration and issuedAt fields.
     *
     * @return side effect that evaluates to [[AuthResponse]]
     */
    def auth: F[AuthResponse] =
      for {
        stateOpt <- ref.get
        newState <- getAuthResponse(stateOpt)
        _ <- ref.set(
              Some(newState)
            )
      } yield newState.authResponse

    private def getAuthResponse(authStateOpt: Option[AuthState]): F[AuthState] =
      authStateOpt match {
        case Some(state) =>
          val expiresIn = state.expiresAt - currentTimestamp()
          if (expiresIn < minute)
            makeRequest.map(authResponse => AuthState(authResponse, currentTimestamp() + authResponse.expires_in))
          else
            F.point(state.copy(authResponse = state.authResponse.copy(expires_in = expiresIn)))
        case None =>
          makeInitialRequest.map(authResponse => AuthState(authResponse, currentTimestamp() + authResponse.expires_in))
      }

    private def makeInitialRequest: F[AuthResponse] =
      for {
        privateKey <- SHA256withRSA.buildPrivateKey[F](settings.privateKey.base64Bytes)
        jwtToken   <- JWTSig.signToString[F, SHA256withRSA](getClaims(settings.claims), privateKey)
        request    <- createRequest(jwtToken, settings)
        response   <- client.expect[AuthResponse](request)
      } yield response

    private def makeRequest: F[AuthResponse] =
      for {
        privateKey <- SHA256withRSA.buildPrivateKey[F](settings.privateKey.base64Bytes)
        jwtToken   <- JWTSig.signToString[F, SHA256withRSA](newClaim, privateKey)
        request    <- createRequest(jwtToken, settings)
        response   <- client.expect[AuthResponse](request)
      } yield response

    private def createRequest(jwtToken: String, settings: Settings): F[Request[F]] =
      for {
        uri <- Uri.fromString(settings.uri).toTry.liftTo[F]
      } yield
        Request[F](
          method = Method.POST,
          uri = uri
        ).withEntity(
          UrlForm(
            "grant_type" -> settings.grantType,
            "assertion"  -> jwtToken
          )
        )

    private def currentTimestamp(): Long = Instant.now.getEpochSecond

    private def newClaim: JWTClaims = getClaims(
      settings.claims.copy(
        issuedAt = Instant.now(),
        expiration = Instant.now().plusSeconds(hour)
      )
    )
  }

  /**
   * Performs single authorization request.
   *
   * @param settings [[Settings]]
   * @param executionContext execution context used to make http requests
   *
   * @return side effect that evaluates to [[AuthResponse]]
   */
  def auth[F[_]: ConcurrentEffect](settings: Settings, executionContext: ExecutionContext): F[AuthResponse] =
    resource(settings, executionContext).use(_.auth)

  /**
   * Gives access to [[Authenticator]] resource that can be used to make multiple authorization requests over time.
   * It creates http client when it is used and reuses it for every new request. Http client is
   * released when resource is released.
   * Responses are cached until token is near to expire (under one minute). After that it will make new
   * authorization request and cache new token.
   *
   * @param settings [[Settings]]
   * @param executionContext execution context used to make http requests
   *
   * @return [[Authenticator]] resource that can be used to make multiple authorization requests over time.
   */
  def resource[F[_]: ConcurrentEffect](
    settings: Settings,
    executionContext: ExecutionContext
  ): Resource[F, Authenticator[F]] =
    BlazeClientBuilder[F](executionContext).resource.flatMap { client =>
      Resource.liftF(Ref.of[F, Option[AuthState]](None)).map(ref => new Authenticator[F](ref, client, settings))
    }

  /**
   * Exposes stream of [[AuthResponse]]s that can be used to automatically make authorization requests over time.
   * Responses are cached until token is near to expire (under one minute). After that it will make new
   * authorization request and cache new token.
   *
   * @param settings [[Settings]]
   * @param executionContext execution context used to make http requests
   *
   * @return stream of [[AuthResponse]]s
   */
  def stream[F[_]: ConcurrentEffect](
    settings: Settings,
    executionContext: ExecutionContext
  ): Stream[F, AuthResponse] =
    Stream
      .resource(resource(settings, executionContext))
      .flatMap(authenticator => Stream.repeatEval(authenticator.auth))

  private def getClaims(googleClaims: GoogleClaims): JWTClaims =
    JWTClaims(
      issuer = Some(googleClaims.issuer),
      audience = Some(JWTSingleAudience(googleClaims.audience)),
      expiration = Some(googleClaims.expiration),
      issuedAt = Some(googleClaims.issuedAt),
      customFields = Seq(("scope", googleClaims.scope.asJson)),
      subject = googleClaims.subject
    )
}
