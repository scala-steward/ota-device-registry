/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import akka.http.scaladsl.marshalling.Marshaller._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.advancedtelematic.libats.auth.{AuthedNamespaceScope, Scopes}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.ota.deviceregistry.data.Group.Name
import com.advancedtelematic.ota.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.ota.deviceregistry.data.{GroupType, Uuid}
import com.advancedtelematic.ota.deviceregistry.db.{GroupInfoRepository, GroupMemberRepository}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class GroupsResource(
    namespaceExtractor: Directive1[AuthedNamespaceScope],
    deviceNamespaceAuthorizer: Directive1[Uuid]
)(implicit ec: ExecutionContext, db: Database)
    extends Directives {

  import UuidDirectives._
  import com.advancedtelematic.libats.http.RefinedMarshallingSupport._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  private val extractGroupId = allowExtractor(namespaceExtractor, extractUuid, groupAllowed)

  private def groupAllowed(groupId: Uuid): Future[Namespace] =
    db.run(GroupInfoRepository.groupInfoNamespace(groupId))

  val groupMembership = new GroupMembership()

  def getDevicesInGroup(groupId: Uuid): Route =
    parameters(('offset.as[Long].?, 'limit.as[Long].?)) { (offset, limit) =>
      complete(groupMembership.listDevices(groupId, offset, limit))
    }

  def listGroups(ns: Namespace): Route =
    parameters(('offset.as[Long].?, 'limit.as[Long].?)) { (offset, limit) =>
      complete(db.run(GroupInfoRepository.list(ns, offset, limit)))
    }

  def getGroup(groupId: Uuid): Route =
    complete(db.run(GroupInfoRepository.findByIdAction(groupId)))

  def createGroup(id: Uuid, groupName: Name, namespace: Namespace, groupType: GroupType, expression: String): Route =
    complete(StatusCodes.Created -> db.run(GroupInfoRepository.create(id, groupName, namespace, groupType, expression)))

  def renameGroup(groupId: Uuid, newGroupName: Name): Route =
    complete(db.run(GroupInfoRepository.renameGroup(groupId, newGroupName)))

  def countDevices(groupId: Uuid): Route =
    complete(groupMembership.countDevices(groupId))

  def addDeviceToGroup(groupId: Uuid, deviceId: Uuid): Route =
    complete(groupMembership.addGroupMember(groupId, deviceId))

  def removeDeviceFromGroup(groupId: Uuid, deviceId: Uuid): Route =
    complete(db.run(GroupMemberRepository.removeGroupMember(groupId, deviceId)))

  implicit val groupTypeParamUnmarshaller = Unmarshaller.strict[String, GroupType](GroupType.withName)

  val route: Route =
    (pathPrefix("device_groups") & namespaceExtractor) { ns =>
      val scope = Scopes.devices(ns)
      (scope.post & parameter('groupName.as[Name]) & parameter('type.as[GroupType]) & parameter('expression.as[String]) & pathEnd) {
        (groupName, `type`, expression) =>
          createGroup(Uuid.generate(), groupName, ns.namespace, `type`, expression)
      } ~
      (scope.get & pathEnd) {
        listGroups(ns.namespace)
      } ~
      (scope.get & extractGroupId & pathEndOrSingleSlash) { groupId =>
        getGroup(groupId)
      } ~
      (scope.get & extractGroupId & path("devices")) { groupId =>
        getDevicesInGroup(groupId)
      } ~
      extractGroupId { groupId =>
        (scope.post & pathPrefix("devices") & deviceNamespaceAuthorizer) { deviceId =>
          addDeviceToGroup(groupId, deviceId)
        } ~
        (scope.delete & pathPrefix("devices") & deviceNamespaceAuthorizer) { deviceId =>
          removeDeviceFromGroup(groupId, deviceId)
        } ~
        (scope.put & path("rename") & parameter('groupName.as[Name])) { groupName =>
          renameGroup(groupId, groupName)
        } ~
        (scope.get & path("count") & pathEnd) {
          countDevices(groupId)
        }
      }
    }
}
