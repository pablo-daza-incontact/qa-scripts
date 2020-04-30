package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scala.concurrent.duration._

class UpdateCases extends Simulation {

  private def httpConf: HttpProtocolBuilder = http.baseUrl(getProperty("PLATFORM_API_URL", "https://app-de-na1.dev.niceincontact.com"))
  private def email: String = getProperty("EMAIL", "manager0001.loadtestgbu.do0098.10@niceincontact.com")
  private def newCase: String = getProperty("NEW", "new")
  private def openCase: String = getProperty("OPEN", "open")
  private def queue: String = getProperty("QUEUE", "f0272dbb-4ea5-405a-935e-f6b985694d0b")
  private def password: String = getProperty("PASSWORD", "j!sFcWDpW92NgA9p8")
  private def newStatus: String = getProperty("STATUS", "resolved")

  /*** Get Global Property Method ***/
  private def getProperty(propertyName: String, defaultValue: String): String = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }

  /*** Before ***/
  before {
    println(s"Updating all cases from Queue: $queue")
    println(s"Updating to Status: $newStatus")
  }
  private val url: String = s"/internal/2.0/cases?&status[]=$newCase&status[]=$openCase&routingQueueId[]=$queue&sorting=createdAt&sortingType=desc"

  val updateCases = scenario("Update Brand Embassy cases")
    .exec(
      http("login BE")
        .post("/system/auth")
        .disableFollowRedirect
        .formParam("email", s"$email")
        .formParam("password", s"$password")
        .formParam("hashUrlAfterLogin", "")
        .formParam("_form_", "login")
        .check(status.is(302))
    )
    .exec(
      http("Read BE Generated cases")
        .get(url)
        .check(status.is(200))
        .check(jsonPath("$.data[*].id").findAll.saveAs("cases"))
        .check(jsonPath("$.scrollToken").exists.saveAs("scrollToken"))
    )
    .asLongAs(session => session("scrollToken").as[String] != "") {
      tryMax(5) {
        exec(
          foreach("${cases}", "case") {
            exec(
              http("Update BE case")
                .patch("/internal/2.0/cases/${case}")
                .body(StringBody(session => {
                  s"""{"status": "$newStatus"}"""
                })).asJson
                .check(status.is(204))
            )
          }
        )
          .exec(
            http("Read BE Generated cases")
              .get(url + "&scrollToken=${scrollToken}")
              .check(status.is(200))
              .check(jsonPath("$.data[*].id").findAll.saveAs("cases"))
              .check(jsonPath("$.scrollToken").exists.saveAs("scrollToken")))
      }.exitHereIfFailed
    }
  setUp(updateCases.inject(rampUsers(1) during (1 seconds))).protocols(httpConf)

  /*** After ***/
  after {
    println("Updating completed")
  }
}
