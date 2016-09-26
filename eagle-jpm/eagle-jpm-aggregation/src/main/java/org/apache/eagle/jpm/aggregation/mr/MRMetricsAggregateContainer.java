/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.eagle.jpm.aggregation.mr;

import org.apache.eagle.jpm.aggregation.AggregationConfig;
import org.apache.eagle.jpm.aggregation.common.MetricAggregator;
import org.apache.eagle.jpm.aggregation.common.MetricsAggregateContainer;
import org.apache.eagle.jpm.mr.historyentity.JobProcessTimeStampEntity;
import org.apache.eagle.jpm.util.Constants;
import org.apache.eagle.log.entity.GenericServiceAPIResponseEntity;
import org.apache.eagle.service.client.IEagleServiceClient;
import org.apache.eagle.service.client.impl.EagleServiceClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MRMetricsAggregateContainer implements MetricsAggregateContainer, Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(MRMetricsAggregateContainer.class);

    private Map<String, MetricAggregator> metricAggregators;

    public MRMetricsAggregateContainer(Map<String, List<List<String>>> metrics) {
        this.metricAggregators = new HashMap<>();
        //metric name, aggregate columns
        for (String metric : metrics.keySet()) {
            this.metricAggregators.put(metric, new MRMetricAggregator(metric, metrics.get(metric)));
        }
    }

    @Override
    public long fetchLatestJobProcessTime() {
        try {
            IEagleServiceClient client = new EagleServiceClientImpl(
                AggregationConfig.get().getEagleServiceConfig().eagleServiceHost,
                AggregationConfig.get().getEagleServiceConfig().eagleServicePort,
                AggregationConfig.get().getEagleServiceConfig().username,
                AggregationConfig.get().getEagleServiceConfig().password);

            String query = String.format("%s[@site=\"%s\"]<@site>{max(currentTimeStamp)}",
                Constants.JPA_JOB_PROCESS_TIME_STAMP_NAME,
                AggregationConfig.get().getJobExtractorConfig().site);

            GenericServiceAPIResponseEntity response = client
                .search(query)
                .startTime(0L)
                .endTime(System.currentTimeMillis())
                .pageSize(10)
                .send();

            List<JobProcessTimeStampEntity> entities = response.getObj();
            return entities.get(0).getCurrentTimeStamp();
        } catch (Exception e) {
            LOG.warn("{}", e);
        }
        return 0L;
    }

    @Override
    public boolean aggregate(long startTime, long endTime) {
        for (String metric : this.metricAggregators.keySet()) {
            MetricAggregator metricAggregator = this.metricAggregators.get(metric);
            if (!metricAggregator.aggregate(startTime, endTime)) {
                return false;
            }
        }
        return true;
    }
}