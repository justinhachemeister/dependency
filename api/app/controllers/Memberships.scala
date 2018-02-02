package controllers

import com.bryzek.dependency.v0.models.json._
import com.bryzek.dependency.v0.models.{Membership, MembershipForm, Organization, Role}
import db.{Authorization, MembershipsDao, OrganizationsDao}
import io.flow.common.v0.models.UserReference
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._
import io.flow.error.v0.models.json._

@javax.inject.Singleton
class Memberships @javax.inject.Inject() (
  membershipsDao: MembershipsDao,
  organizationsDao: OrganizationsDao,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends FlowController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    userId: Option[String],
    role: Option[Role],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        membershipsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          organization = organization,
          userId = userId,
          role = role,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withMembership(request.user, id) { membership =>
      Ok(Json.toJson(membership))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[MembershipForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[MembershipForm] => {
        membershipsDao.create(request.user, s.get, organizationsDao.findByKey) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(membership) => Created(Json.toJson(membership))
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    withMembership(request.user, id) { membership =>
      membershipsDao.delete(request.user, membership)
      NoContent
    }
  }

  def withMembership(user: UserReference, id: String)(
    f: Membership => Result
  ): Result = {
    membershipsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(membership) => {
        f(membership)
      }
    }
  }

}
