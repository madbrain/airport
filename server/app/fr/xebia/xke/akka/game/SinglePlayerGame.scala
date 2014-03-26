package fr.xebia.xke.akka.game

import akka.actor._
import concurrent.duration._
import languageFeature.postfixOps
import akka.event.EventStream
import scala.util.Random
import fr.xebia.xke.akka.plane.Plane
import fr.xebia.xke.akka.airport._
import fr.xebia.xke.akka.airport.InitGroundControl
import fr.xebia.xke.akka.plane.PlaneListener
import scala.Some
import akka.actor.OneForOneStrategy
import akka.actor.Terminated
import fr.xebia.xke.akka.airport.InitAirTrafficControl
import fr.xebia.xke.akka.game.SinglePlayerGame.NewPlane
import fr.xebia.xke.akka.plane.event.PlaneStatus

class SinglePlayerGame(settings: Settings, planeType: Class[Plane], gameEventStream: EventStream, airport: Airport) extends Actor with ActorLogging {

  import settings._

  val runways: Seq[ActorRef] =
    for (i <- 1 to nrOfRunways)
    yield context.actorOf(Props[Runway], s"runway-$i")

  val taxiways: Seq[ActorRef] =
    for (i <- 1 to nrOfTaxiways)
    yield context.actorOf(Props(classOf[Taxiway], settings), s"taxiway-$i")

  val gates: Seq[ActorRef] =
    for (i <- 1 to nrOfGates)
    yield context.actorOf(Props[Gate], s"gate-$i")

  var planeGeneration: Cancellable = null

  var score = 0

  var planesToGenerate: Int = settings.objective / 2

  override def preStart() {
    runways.foreach(context.watch)
    taxiways.foreach(context.watch)
    gates.foreach(context.watch)

    gameEventStream.subscribe(self, classOf[PlaneStatus])

    publishScore()
  }

  def receive = idle

  def idle: Receive = {
    case InitGame(airTrafficControlLookup, groundControlLookup) =>

      airTrafficControlLookup ! InitAirTrafficControl(runways, settings.ackMaxDuration)
      groundControlLookup ! InitGroundControl(taxiways, gates, taxiwayCapacity, settings.ackMaxDuration)

      context become waitingForTheGameReady(None, None)
  }

  def waitingForTheGameReady(registeredAirTrafficControl: Option[ActorRef], registeredGroundControl: Option[ActorRef]): Receive = {
    case AirTrafficControlReady =>
      val airTrafficControl = sender
      context watch airTrafficControl

      registeredGroundControl match {
        case None =>
          context become waitingForTheGameReady(Some(airTrafficControl), None)

        case Some(groundControl) =>
          import context.dispatcher
          planeGeneration = context.system.scheduler.schedule(1 second, planeGenerationInterval milliseconds, self, NewPlane)

          context become started(airTrafficControl, groundControl)
      }

    case GroundControlReady =>
      val groundControl = sender
      context watch groundControl

      registeredAirTrafficControl match {
        case None =>
          context become waitingForTheGameReady(null, Some(groundControl))

        case Some(airTrafficControl) =>
          import context.dispatcher
          planeGeneration = context.system.scheduler.schedule(1 second, planeGenerationInterval milliseconds, self, NewPlane)

          context become started(airTrafficControl, groundControl)
      }
  }

  def started(airTrafficControl: ActorRef, groundControl: ActorRef): Receive = {
    case PlaneStatus("done", _, _, _) =>
      gain()

    case PlaneStatus(_, _, _, error) if error.nonEmpty =>
      loose()

    case Terminated(_) =>
      gameEventStream.publish(GameOver)
      context stop self

    case NewPlane if planesToGenerate > 0 =>

      val anArrivalFlight = airport.arrivals(Random.nextInt(airport.arrivals.size))

      val planeEventStream = new EventStream()

      val plane = context.actorOf(Props(planeType, airTrafficControl, self, settings, planeEventStream), s"${anArrivalFlight.airline}-${Random.nextLong() % 100000}")
      val listener = context.actorOf(Props(classOf[PlaneListener], plane, gameEventStream))

      planeEventStream.subscribe(listener, classOf[Any])

      planesToGenerate -= 1
  }

  private def publishScore() {
    gameEventStream.publish(Score(score, objective))
  }

  private def gain() {
    score = Math.min(score + 2, objective)
    publishScore()

    if (score == objective) {
      gameEventStream.publish(GameEnd)
      planeGeneration.cancel()

      context stop self
    }
  }

  private def loose() {
    score = Math.max(score - 3, 0)
    publishScore()
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

}

object SinglePlayerGame {

  def props(settings: Settings, planeType: Class[_ <: Plane], gameEventStream: EventStream, airport: Airport): Props =
    Props(classOf[SinglePlayerGame], settings, planeType, gameEventStream, airport)

  case object NewPlane

  case class ErrorInGame(cause: String) extends Exception(cause)

}