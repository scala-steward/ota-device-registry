/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import com.advancedtelematic.ota.deviceregistry.data.CredentialsType
import slick.jdbc.MySQLProfile.api._

object SlickMappings {

  implicit val enumMapperCredentialsType =
    MappedColumnType.base[CredentialsType.CredentialsType, String](
      _.toString,
      (s: String) => CredentialsType.withName(s)
    )
}
