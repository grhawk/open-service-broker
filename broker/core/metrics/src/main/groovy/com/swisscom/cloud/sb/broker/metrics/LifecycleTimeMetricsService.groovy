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

package com.swisscom.cloud.sb.broker.metrics

import com.swisscom.cloud.sb.broker.model.Plan
import groovy.transform.CompileStatic
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
class LifecycleTimeMetricsService extends PlanBasedMetricsService {

    static String LIFECYCLE_TIME_KEY = "LifecycleTime"

    @Autowired
    protected LifecycleTimeMetricsService(MeterRegistry meterRegistry, MetricsCache metricsCache, ServiceBrokerMetricsConfig serviceBrokerMetricsConfig) {
        super(meterRegistry, metricsCache, serviceBrokerMetricsConfig)
    }

    @Override
    void bindMetricsPerPlan(Plan plan) {
        addMetricsGauge(plan, LIFECYCLE_TIME_KEY,{ metricsCache.serviceInstanceList.byPlanId(plan.guid).lifecycleTimeInSeconds() })
    }
}