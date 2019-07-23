package io.github.jkobejs.google.oauth4s

import cats.implicits._
import java.nio.file.Paths
import cats.effect.Sync
import cats.effect.ContextShift
import scala.concurrent.ExecutionContext
import io.circe.generic.auto._, io.circe.parser._

/**
 * `ConfigReader` provides api for reading service account key file from file system.
 */
object ServiceAccountKeyReader {

  /**
   * Google Cloud service account key created using the GCP Console or the gcloud command-line tool.
   *
   * @see https://cloud.google.com/iam/docs/creating-managing-service-account-keys
   */
  case class ServiceAccountKey(
    `type`: String,
    project_id: String,
    private_key_id: String,
    private_key: String,
    client_email: String,
    client_id: String,
    auth_uri: String,
    token_uri: String,
    auth_provider_x509_cert_url: String,
    client_x509_cert_url: String
  )

  /**
   * Reads Google Cloud service account key created using the GCP Console or the gcloud command-line tool.
   *
   * @see https://cloud.google.com/iam/docs/creating-managing-service-account-keys
   *
   * @param path path to service account key file
   * @param blockingExecutionContext `ExecutionContext` used to read service account key file, it should be blocking execution context
   *
   * @return  side effect that evaluates to [[ServiceAccountKey]]
   */
  def readServiceAccountKey[F[_]: ContextShift](path: String, blockingExecutionContext: ExecutionContext)(
    implicit F: Sync[F]
  ): F[ServiceAccountKey] =
    F.catchNonFatal(Paths.get(path))
      .flatMap(
        jPath =>
          fs2.io.file
            .readAll[F](jPath, blockingExecutionContext, 4096)
            .through(fs2.text.utf8Decode)
            .compile
            .toList
            .map(_.mkString)
            .flatMap(string => F.fromEither(decode[ServiceAccountKey](string)))
      )

}
