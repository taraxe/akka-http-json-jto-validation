package com

import play.api.libs.json._
import jto.validation._
import playjson.Writes._

package object taraxe {
  def toJson(errs: Seq[(Path, Seq[ValidationError])]): JsValue = {
    (Path \ "errors").write[Seq[(Path, Seq[ValidationError])], JsObject].writes(errs)
  }
}
