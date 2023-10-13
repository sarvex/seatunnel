/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.fake.source;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import org.apache.seatunnel.api.common.JobContext;
import org.apache.seatunnel.api.common.SeaTunnelAPIErrorCode;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Boundedness;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.source.SupportColumnProjection;
import org.apache.seatunnel.api.source.SupportParallelism;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.api.table.catalog.schema.TableSchemaOptions;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.constants.JobMode;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.connectors.seatunnel.fake.config.FakeConfig;
import org.apache.seatunnel.connectors.seatunnel.fake.exception.FakeConnectorException;
import org.apache.seatunnel.connectors.seatunnel.fake.state.FakeSourceState;

import com.google.auto.service.AutoService;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@AutoService(SeaTunnelSource.class)
public class FakeSource
        implements SeaTunnelSource<SeaTunnelRow, FakeSourceSplit, FakeSourceState>,
                SupportParallelism,
                SupportColumnProjection {

    private JobContext jobContext;
    private CatalogTable catalogTable;
    private FakeConfig fakeConfig;

    public FakeSource() {}

    public FakeSource(ReadonlyConfig readonlyConfig) {
        this.catalogTable = CatalogTableUtil.buildWithConfig(readonlyConfig);
        this.fakeConfig = FakeConfig.buildWithConfig(readonlyConfig.toConfig());
    }

    @Override
    public Boundedness getBoundedness() {
        return JobMode.BATCH.equals(jobContext.getJobMode())
                ? Boundedness.BOUNDED
                : Boundedness.UNBOUNDED;
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        if (fakeConfig.getTableIdentifiers().isEmpty()) {
            return Lists.newArrayList(catalogTable);
        } else {
            return fakeConfig.getTableIdentifiers().stream()
                    .map(
                            tableIdentifier ->
                                    CatalogTable.of(
                                            tableIdentifier,
                                            catalogTable.getTableSchema(),
                                            catalogTable.getOptions(),
                                            catalogTable.getPartitionKeys(),
                                            catalogTable.getComment()))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public SeaTunnelRowType getProducedType() {
        return catalogTable.getSeaTunnelRowType();
    }

    @Override
    public SourceSplitEnumerator<FakeSourceSplit, FakeSourceState> createEnumerator(
            SourceSplitEnumerator.Context<FakeSourceSplit> enumeratorContext) throws Exception {
        return new FakeSourceSplitEnumerator(enumeratorContext, fakeConfig, Collections.emptySet());
    }

    @Override
    public SourceSplitEnumerator<FakeSourceSplit, FakeSourceState> restoreEnumerator(
            SourceSplitEnumerator.Context<FakeSourceSplit> enumeratorContext,
            FakeSourceState checkpointState) {
        return new FakeSourceSplitEnumerator(
                enumeratorContext, fakeConfig, checkpointState.getAssignedSplits());
    }

    @Override
    public SourceReader<SeaTunnelRow, FakeSourceSplit> createReader(
            SourceReader.Context readerContext) {
        return new FakeSourceReader(readerContext, catalogTable.getSeaTunnelRowType(), fakeConfig);
    }

    @Override
    public String getPluginName() {
        return "FakeSource";
    }

    @Override
    public void prepare(Config pluginConfig) {
        CheckResult result =
                CheckConfigUtil.checkAllExists(pluginConfig, TableSchemaOptions.SCHEMA.key());
        if (!result.isSuccess()) {
            throw new FakeConnectorException(
                    SeaTunnelAPIErrorCode.CONFIG_VALIDATION_FAILED,
                    String.format(
                            "PluginName: %s, PluginType: %s, Message: %s",
                            getPluginName(), PluginType.SOURCE, result.getMsg()));
        }
        this.catalogTable = CatalogTableUtil.buildWithConfig(pluginConfig);
        this.fakeConfig = FakeConfig.buildWithConfig(pluginConfig);
    }

    @Override
    public void setJobContext(JobContext jobContext) {
        this.jobContext = jobContext;
    }
}
