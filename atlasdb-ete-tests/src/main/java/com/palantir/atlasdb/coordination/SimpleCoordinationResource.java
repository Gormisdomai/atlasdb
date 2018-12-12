/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.coordination;

import java.util.UUID;

import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.internalschema.TransactionSchemaManager;
import com.palantir.atlasdb.internalschema.persistence.CoordinationServices;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.transaction.api.TransactionManager;
import com.palantir.logsafe.SafeArg;

public final class SimpleCoordinationResource implements CoordinationResource {
    private static final TableReference TEST_TABLE = TableReference.createFromFullyQualifiedName(
            "test." + SimpleCoordinationResource.class.getSimpleName());

    private final TransactionManager transactionManager;
    private final TransactionSchemaManager transactionSchemaManager;

    private SimpleCoordinationResource(
            TransactionManager transactionManager,
            TransactionSchemaManager transactionSchemaManager) {
        this.transactionManager = transactionManager;
        this.transactionSchemaManager = transactionSchemaManager;
    }

    public static CoordinationResource create(TransactionManager transactionManager) {
        return new SimpleCoordinationResource(transactionManager,
                new TransactionSchemaManager(
                        CoordinationServices.createDefault(
                        transactionManager.getKeyValueService(),
                        transactionManager.getTimestampService(),
                        false)));
    }

    @Override
    public int getTransactionsSchemaVersion(long timestamp) {
        return transactionSchemaManager.getTransactionsSchemaVersion(timestamp);
    }

    @Override
    public boolean tryInstallNewTransactionsSchemaVersion(int newVersion) {
        return transactionSchemaManager.tryInstallNewTransactionsSchemaVersion(newVersion);
    }

    @Override
    public void forceInstallNewTransactionsSchemaVersion(int newVersion) {
        while (transactionSchemaManager.getTransactionsSchemaVersion(
                transactionManager.getTimestampService().getFreshTimestamp()) != newVersion) {
            LoggerFactory.getLogger(SimpleCoordinationResource.class).info("ts = {}",
                    SafeArg.of("ts", transactionManager.getTimestampService().getFreshTimestamp()));
            transactionSchemaManager.tryInstallNewTransactionsSchemaVersion(newVersion);
            advanceOneHundredMillionTimestamps();
        }
    }

    @Override
    public boolean doTransactionAndReportOutcome() {
        try {
            return transactionManager.runTaskThrowOnConflict(tx -> {
                KeyValueService kvs = transactionManager.getKeyValueService();
                kvs.createTable(TEST_TABLE, AtlasDbConstants.GENERIC_TABLE_METADATA);

                tx.put(TEST_TABLE, ImmutableMap.of(generateRandomCell(), new byte[1]));
                LoggerFactory.getLogger(SimpleCoordinationResource.class).info("tx ts = {}",
                        SafeArg.of("ts", tx.getTimestamp()));
                return true;
            });
        } catch (Exception e) {
            return false;
        }
    }

    private void advanceOneHundredMillionTimestamps() {
        transactionManager.getTimestampManagementService().fastForwardTimestamp(
                transactionManager.getTimestampService().getFreshTimestamp() + 100_000_000);
    }

    private static Cell generateRandomCell() {
        return Cell.create(
                PtBytes.toBytes(UUID.randomUUID().getMostSignificantBits()),
                PtBytes.toBytes(UUID.randomUUID().getMostSignificantBits()));
    }
}
