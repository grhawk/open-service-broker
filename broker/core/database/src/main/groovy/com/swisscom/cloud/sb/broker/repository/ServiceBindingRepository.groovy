/*
 * Copyright (c) 2018 Swisscom (Switzerland) Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.swisscom.cloud.sb.broker.repository

import com.swisscom.cloud.sb.broker.model.ServiceBinding
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ServiceBindingRepository extends BaseRepository<ServiceBinding, Integer> {
    ServiceBinding findByGuid(String guid)

    @Query("select sb from ServiceBinding sb LEFT JOIN FETCH sb.details where sb.guid = (:guid) ")
    ServiceBinding findByGuidAndFetchDetailsEagerly(@Param("guid") String guid)

    @Query("select sb from ServiceBinding sb where sb.credhubCredentialId is null")
    List<ServiceBinding> findNotMigratedCredHubBindings()
}
