/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.keyvalue.cassandra;

import static org.mockito.Mockito.mock;

import org.junit.ClassRule;

import com.palantir.atlasdb.containers.CassandraContainer;
import com.palantir.atlasdb.containers.Containers;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.sweep.queue.MultiTableSweepQueueWriter;
import com.palantir.atlasdb.sweep.queue.SpecialTimestampsSupplier;
import com.palantir.atlasdb.sweep.queue.TargetedSweepFollower;
import com.palantir.atlasdb.sweep.queue.TargetedSweeper;
import com.palantir.atlasdb.transaction.impl.AbstractSerializableTransactionWithCellLockTest;
import com.palantir.lock.v2.TimelockService;

public class CassandraKvsSerializableTransactionWithCellLockTest
        extends AbstractSerializableTransactionWithCellLockTest {
    @ClassRule
    public static final Containers CONTAINERS =
            new Containers(CassandraKvsSerializableTransactionWithCellLockTest.class)
                    .with(new CassandraContainer());

    @Override
    protected KeyValueService getKeyValueService() {
        return CassandraKeyValueServiceImpl.createForTesting(
                CassandraContainer.KVS_CONFIG,
                CassandraContainer.LEADER_CONFIG);
    }

    @Override
    protected MultiTableSweepQueueWriter getSweepQueueWriterUninitialized() {
        return TargetedSweeper.createUninitializedForTest(() -> 128);
    }

    @Override
    protected MultiTableSweepQueueWriter getSweepQueueWriterInitialized() {
        TargetedSweeper queue = TargetedSweeper.createUninitializedForTest(() -> 128);
        queue.initialize(new SpecialTimestampsSupplier(() -> 0, () -> 0), mock(TimelockService.class), keyValueService,
                mock(TargetedSweepFollower.class));
        return queue;
    }

    @Override
    protected boolean supportsReverse() {
        return false;
    }
}