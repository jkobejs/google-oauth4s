package io.github.jkobejs.google.oauth4s

import cats.effect.IO

import scala.concurrent.ExecutionContext.global
import org.scalatest.FunSuite
import java.nio.file.NoSuchFileException

class ServiceAccountKeyReaderSuite extends FunSuite {
  implicit val ctx   = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  test(".readServiceAccountKey fails on non existing path") {
    val result = ServiceAccountKeyReader.readServiceAccountKey[IO]("non-existing", global).attempt.unsafeRunSync
    assert(result.isLeft)
    assert(result.left.get.isInstanceOf[NoSuchFileException])
  }

  test(".readServiceAccountKey return service account for valid path") {
    val serviceAccount =
      ServiceAccountKeyReader.readServiceAccountKey[IO]("src/test/resources/service-account.json", global).unsafeRunSync
    assertResult(
      ServiceAccountKeyReader.ServiceAccountKey(
        `type` = "service_account",
        project_id = "projectId",
        private_key_id = "privateKeyId",
        private_key = "privateKey",
        client_email = "clientEmail",
        client_id = "clientId",
        auth_uri = "https://accounts.google.com/o/oauth2/auth",
        token_uri = "https://oauth2.googleapis.com/token",
        auth_provider_x509_cert_url = "https://www.googleapis.com/oauth2/v1/certs",
        client_x509_cert_url =
          "https://www.googleapis.com/robot/v1/metadata/x509/[client_email].iam.gserviceaccount.com"
      )
    )(serviceAccount)
  }
}
