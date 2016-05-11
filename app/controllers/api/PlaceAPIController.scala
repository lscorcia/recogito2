package controllers.api

import controllers.{ BaseController, HasPrettyPrintJSON }
import javax.inject.Inject
import models.user.Roles._
import models.place.PlaceService
import play.api.cache.CacheApi
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext
import storage.DB

class PlaceAPIController @Inject() (implicit val cache: CacheApi, val db: DB, context: ExecutionContext) extends BaseController with HasPrettyPrintJSON {
  
  def searchPlaces(query: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    PlaceService.searchPlaces(query).map { results => 
      jsonOk(Json.toJson(results.map(_._1)))
    }
  }
  
  def findPlaceByURI(uri: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    PlaceService.findByURI(uri).map { _ match {
      case Some((place, _)) => jsonOk(Json.toJson(place))
      case None => NotFound
    }}
  }
  
  def getPlacesInDocument(docId: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    PlaceService.findPlacesInDocument(docId).map(tuples => jsonOk(Json.toJson(tuples.map(_._1))))
  }
  
  def searchPlacesInDocument(query: String, docId: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    PlaceService.searchPlacesInDocument(query, docId).map { results =>
      jsonOk(Json.toJson(results.map(_._1)))
    }
  }
 
}