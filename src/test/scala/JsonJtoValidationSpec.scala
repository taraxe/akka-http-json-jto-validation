package com.taraxe

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.libs.json._
import jto.validation._

import scala.language.postfixOps
import scala.concurrent.{ Await, Awaitable }
import scala.concurrent.duration._

class JsonJtoValidationSupportSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll with JsonJtoValidationSupport {

  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  def await[T] = Await.result(_: Awaitable[T], 1 seconds)

  "jsonUnmarshaller" should "unmarshal a JsValue" in {
    val test = Unmarshal(HttpEntity(JsString("").toString())).to[JsValue]
    test.futureValue shouldBe JsString("")
  }

  "jsonUnmarshaller" should "fail to unmarshal an invalid JsValue" in {
    val test = Unmarshal(HttpEntity("invalid")).to[JsValue]
    assertThrows[com.fasterxml.jackson.core.JsonParseException](await(test))
  }

  "jsonUnmarshaller" should "unmarshal any object as JsValue given a Json Rule" in {
    val message = Message("test")
    val test = Unmarshal(HttpEntity(Message.writer.writes(message).toString)).to[Message]
    test.futureValue shouldBe message
  }

  "jsonUnmarshaller" should "fail to unmarshal an object providing a Json Rule from an invalid json" in {
    val message = Message("test message")
    val test = Unmarshal(HttpEntity("""{"foo": "bar"}""")).to[Message]
    assertThrows[akka.http.scaladsl.server.RejectionError](await(test))
  }

  def sinkFold = Sink.fold[String, ByteString]("") { (acc, in) ⇒ acc + in.utf8String }
  "marshaller" should "marshal as JsValue" in {
    val json = JsString("test")
    val entity = Marshal(json).to[MessageEntity].futureValue
    entity.contentType shouldEqual ContentTypes.`application/json`
    bodyAsString(entity) shouldEqual json.toString()
  }

  "marshaller" should "marshal any object as JsValue given a Json Write" in {
    val message = Message("test")
    val entity = Marshal(message).to[MessageEntity].futureValue
    entity.contentType shouldEqual ContentTypes.`application/json`
    bodyAsString(entity) shouldEqual Message.writer.writes(message).toString
  }

  def bodyAsString(m: MessageEntity) = m.getDataBytes().runWith(sinkFold, mat).futureValue

}

case class Message(value: String)
object Message {
  import playjson.Rules._
  import playjson.Writes._
  implicit def reader = Rule.gen[JsValue, Message]
  implicit def writer = Write.gen[Message, JsObject]
}
