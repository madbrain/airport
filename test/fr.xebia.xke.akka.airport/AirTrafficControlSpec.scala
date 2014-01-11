package fr.xebia.xke.akka.airport

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import concurrent.duration._
import fr.xebia.xke.akka.airport.Command.Land
import fr.xebia.xke.akka.airport.PlaneEvent.{HasLeft, HasLanded, Incoming}
import language.postfixOps
import org.scalatest.{ShouldMatchers, GivenWhenThen, FunSpec}

class AirTrafficControlSpec extends FunSpec with GivenWhenThen with ShouldMatchers {

  describe("An air traffic control") {

    it("should tell the plane to land when there is a free runway") {

      Given("an air traffic control with 1 runway")
      implicit val system = ActorSystem()
      val runway = TestProbe()
      val airTrafficControl = system.actorOf(Props(classOf[AirTrafficControl], TestProbe().ref, Seq(runway.ref)), "airTrafficControl")

      When("a new plane incomes")
      val plane = TestProbe()
      plane.send(airTrafficControl, Incoming)

      Then("air traffic control should tell the plane to land on the free runway")
      plane.expectMsg(max = 100 milliseconds, Land(runway.ref))
    }

    it("should alternate the runway allocated to a new incoming plane") {
      pending

      Given("an air traffic control with 2 runways")
      implicit val system = ActorSystem()
      val runway1 = TestProbe()
      val runway2 = TestProbe()
      val airTrafficControl = system.actorOf(Props(classOf[AirTrafficControl], TestProbe().ref, Seq(runway1.ref, runway2.ref)), "airTrafficControl")

      When("a new 2 planes income")
      val plane1 = TestProbe()
      plane1.send(airTrafficControl, Incoming)
      val plane2 = TestProbe()
      plane2.send(airTrafficControl, Incoming)

      Then("air traffic control should allocate one of the two free runways to each plane")
      val allocation1: Land = plane1.expectMsgAllClassOf(classOf[Land]).head
      val allocation2: Land = plane2.expectMsgAllClassOf(classOf[Land]).head

      allocation1.runway should (equal(runway1.ref) or equal(runway2.ref))
      allocation2.runway should (equal(runway1.ref) or equal(runway2.ref))

      allocation1.runway should not equal allocation2.runway
    }

    it("should not tell the plane to land until a runway is free") {
      pending

      Given("an air traffic control with 1 runway")
      implicit val system = ActorSystem()
      val runway = TestProbe()
      val airTrafficControl = system.actorOf(Props(classOf[AirTrafficControl], TestProbe().ref, Seq(runway.ref)), "airTrafficControl")

      Given("a first plane has landed on the runway")
      val firstPlane = TestProbe()
      firstPlane.send(airTrafficControl, Incoming)
      firstPlane.expectMsg(100 milliseconds, Land(runway.ref))
      firstPlane reply HasLanded

      When("when a new plane incomes")
      val plane = TestProbe()
      plane.send(airTrafficControl, Incoming)

      Then("air traffic control not should tell anything to the plane")
      plane.expectNoMsg(100 milliseconds)

      When("first plane leaves")
      firstPlane.send(airTrafficControl, HasLeft)

      Then("air traffic control should tell the plane to land on the free runway")
      plane.expectMsg(max = 100 milliseconds, Land(runway.ref))
    }
  }
}