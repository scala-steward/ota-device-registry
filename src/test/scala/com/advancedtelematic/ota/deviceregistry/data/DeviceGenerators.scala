/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import eu.timepit.refined.api.Refined
import org.scalacheck.{Arbitrary, Gen}
import java.time.Instant

import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.data
import com.advancedtelematic.ota.deviceregistry.data.DataType.DeviceT

trait DeviceGenerators {

  import Arbitrary._
  import Device._

  val genDeviceName: Gen[DeviceName] = for {
    //use a minimum length for DeviceName to reduce possibility of naming conflicts
    size <- Gen.choose(10, 100)
    name <- Gen.containerOfN[Seq, Char](size, Gen.alphaNumChar)
  } yield Refined.unsafeApply(name.mkString)

  val genDeviceUUID: Gen[DeviceId] = Gen.delay(DeviceId.generate)

  val genDeviceId: Gen[DeviceOemId] = for {
    size <- Gen.choose(10, 100)
    name <- Gen.containerOfN[Seq, Char](size, Gen.alphaNumChar)
  } yield DeviceOemId(name.mkString)

  val genDeviceType: Gen[DeviceType] = for {
    t <- Gen.oneOf(DeviceType.values.toSeq)
  } yield t

  val genInstant: Gen[Instant] = for {
    millis <- Gen.chooseNum[Long](0, 10000000000000L)
  } yield Instant.ofEpochMilli(millis)

  def genDeviceWith(deviceNameGen: Gen[DeviceName], deviceIdGen: Gen[DeviceOemId]): Gen[Device] =
    for {
      uuid       <- genDeviceUUID
      name       <- deviceNameGen
      deviceId   <- deviceIdGen
      deviceType <- genDeviceType
      lastSeen   <- Gen.option(genInstant)
      activated  <- Gen.option(genInstant)
    } yield data.Device(Namespaces.defaultNs, uuid, name, deviceId, deviceType, lastSeen, Instant.now(), activated)

  val genDevice: Gen[Device] = genDeviceWith(genDeviceName, genDeviceId)

  def genDeviceTWith(deviceNameGen: Gen[DeviceName], deviceIdGen: Gen[DeviceOemId]): Gen[DeviceT] =
    for {
      name       <- deviceNameGen
      deviceId   <- deviceIdGen
      deviceType <- genDeviceType
    } yield DeviceT(name, deviceId, deviceType)

  val genDeviceT: Gen[DeviceT] = genDeviceTWith(genDeviceName, genDeviceId)

  def genConflictFreeDeviceTs(): Gen[Seq[DeviceT]] =
    genConflictFreeDeviceTs(arbitrary[Int].sample.get)

  def genConflictFreeDeviceTs(n: Int): Gen[Seq[DeviceT]] =
    for {
      dns  <- Gen.containerOfN[Seq, DeviceName](n, genDeviceName)
      dids <- Gen.containerOfN[Seq, DeviceOemId](n, genDeviceId)
    } yield {
      dns.zip(dids).map {
        case (nameG, idG) =>
          genDeviceTWith(nameG, idG).sample.get
      }
    }

  implicit lazy val arbDeviceName: Arbitrary[DeviceName] = Arbitrary(genDeviceName)
  implicit lazy val arbDeviceUUID: Arbitrary[DeviceId] = Arbitrary(genDeviceUUID)
  implicit lazy val arbDeviceId: Arbitrary[DeviceOemId]     = Arbitrary(genDeviceId)
  implicit lazy val arbDeviceType: Arbitrary[DeviceType] = Arbitrary(genDeviceType)
  implicit lazy val arbLastSeen: Arbitrary[Instant]      = Arbitrary(genInstant)
  implicit lazy val arbDevice: Arbitrary[Device]         = Arbitrary(genDevice)
  implicit lazy val arbDeviceT: Arbitrary[DeviceT]       = Arbitrary(genDeviceT)

}

object DeviceGenerators extends DeviceGenerators

object InvalidDeviceGenerators extends DeviceGenerators with DeviceIdGenerators {
  val genInvalidVehicle: Gen[Device] = for {
    // TODO: for now, just generate an invalid VIN with a valid namespace
    deviceId <- genInvalidDeviceId
    d        <- genDevice
  } yield d.copy(deviceId = deviceId, namespace = Namespaces.defaultNs)

  def getInvalidVehicle: Device = genInvalidVehicle.sample.getOrElse(getInvalidVehicle)
}
