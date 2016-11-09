package com.taraxe

import scala.concurrent.Future
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.MediaTypes._

import play.api.libs.json._
import jto.validation._
import playjson.Writes._

object JsonJtoValidationSupport extends JsonJtoValidationSupport

trait JsonJtoValidationSupport extends Directives {

  /* Helper directive to extract JsValue */
  type JsReader[A] = Rule[JsValue, A]
  def bodyAsJson = entity(as[JsValue])

  def expect[E](rule: JsReader[E]) = validate(rule).flatMap[Tuple1[E]] {
    case Valid(v)      ⇒ provide(v)
    case Invalid(errs) ⇒ reject(ValidationRejection(toJson(errs).toString, Some(JsonValidationException(errs))))
  }

  def validate[E](rule: JsReader[E]) = bodyAsJson.flatMap[Tuple1[VA[E]]] { js ⇒
    provide(rule.validate(js))
  }

  /* Play Json Unmarshallers */
  implicit def jsonUnmarshaller: Unmarshaller[HttpEntity, JsValue] = Unmarshaller.stringUnmarshaller.map(Json.parse)
  implicit def jsonEntityUnmarshaller[E: JsReader]: Unmarshaller[JsValue, E] =
    Unmarshaller { implicit ec ⇒ js ⇒
      implicitly[JsReader[E]].validate(js) match {
        case Valid(e)      ⇒ Future.successful(e)
        case Invalid(errs) ⇒ Future.failed(RejectionError(ValidationRejection(toJson(errs).toString, Some(JsonValidationException(errs)))))
      }
    }
  implicit def entityUnmarshaller[E: JsReader]: Unmarshaller[HttpEntity, E] =
    jsonUnmarshaller.transform { implicit ec ⇒ implicit mat ⇒ jsF ⇒
      jsF.flatMap(jsonEntityUnmarshaller(implicitly[JsReader[E]])(_))
    }

  /* Play Json Marshallers */
  type JsWriter[A] = Write[A, JsValue]
  implicit def jsonMarshaller: ToEntityMarshaller[JsValue] = Marshaller.StringMarshaller.wrap(`application/json`)((_: JsValue).toString)
  implicit def jsonEntityMarshaller[E: JsWriter]: ToEntityMarshaller[E] = jsonMarshaller.wrap(`application/json`)(implicitly[JsWriter[E]].writes)

}

case class JsonValidationException(errs: Seq[(Path, Seq[ValidationError])]) extends RuntimeException {
  override def getMessage = toJson(errs).toString()
}
