package nl.pragmasoft.traingen

import cats.data.OptionT
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Port, ipv4, port}
import nl.pragmasoft.traingen.http.Resource
import nl.pragmasoft.traingen.http.definitions.ComboMovement
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling

object Main extends IOApp:

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create

  private val httpApp: HttpApp[IO] =
    def errorHandler(t: Throwable, msg: => String): OptionT[IO, Unit] =
      OptionT.liftF(IO.println(t) >> IO(t.printStackTrace()))

    Logger
      .httpApp(logHeaders = true, logBody = true)(
        ErrorAction
          .log(
            Router(
              "/" -> new Resource[IO]().routes(
                new ComboGenerator[IO]:
                  override val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
                // <+>
              )
            ),
            messageFailureLogAction = errorHandler,
            serviceErrorLogAction = errorHandler
          )
          .orNotFound
      )

  private val server = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(httpApp)
    .build

  def run(args: List[String]): IO[ExitCode] = server
    .use(_ => IO.never)
    .as(ExitCode.Success)
