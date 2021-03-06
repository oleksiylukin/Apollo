/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.shard.observer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.apollocurrency.aplwallet.apl.core.app.BlockchainProcessor;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.chainid.HeightConfig;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.dao.ShardDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.ShardRecoveryDao;
import com.apollocurrency.aplwallet.apl.core.shard.ShardMigrationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.CONCURRENT)
public class ShardObserverTest {
    public static final int DEFAULT_SHARDING_FREQUENCY = 5_000;
    public static final int NOT_MULTIPLE_SHARDING_FREQUENCY = 4_999;
    public static final int DEFAULT_MIN_ROLLBACK_HEIGHT = 100_000;
    @Mock
    BlockchainConfig blockchainConfig;
    @Mock
    ShardMigrationExecutor shardMigrationExecutor;
    @Mock
    BlockchainProcessor blockchainProcessor;
    @Mock
    HeightConfig heightConfig;
    @Mock
    ShardDao shardDao;
    @Mock
    ShardRecoveryDao recoveryDao;
    private ShardObserver shardObserver;

    @BeforeEach
    void setUp() {
        doReturn(heightConfig).when(blockchainConfig).getCurrentConfig();
        shardObserver = new ShardObserver(
                blockchainProcessor, blockchainConfig,
                shardMigrationExecutor,
                shardDao, recoveryDao);
    }

    @Test
    void testSkipShardingWhenShardingIsDisabled() throws ExecutionException, InterruptedException {
        doReturn(false).when(heightConfig).isShardingEnabled();

        CompletableFuture<Boolean> c = shardObserver.tryCreateShardAsync();

        assertNull(c);
        verify(shardMigrationExecutor, never()).executeAllOperations();
    }

    @Test
    void testDoNotShardWhenMinRollbackHeightIsNotMultipleOfShardingFrequency() throws ExecutionException, InterruptedException {
        doReturn(DEFAULT_MIN_ROLLBACK_HEIGHT).when(blockchainProcessor).getMinRollbackHeight();
        doReturn(true).when(heightConfig).isShardingEnabled();
        doReturn(NOT_MULTIPLE_SHARDING_FREQUENCY).when(heightConfig).getShardingFrequency();

        CompletableFuture<Boolean> c = shardObserver.tryCreateShardAsync();

        assertNull(c);
        verify(shardMigrationExecutor, never()).executeAllOperations();
    }

    @Test
    void testDoNotShardWhenMinRollbackHeightIsZero() throws ExecutionException, InterruptedException {
        doReturn(0).when(blockchainProcessor).getMinRollbackHeight();
        doReturn(true).when(heightConfig).isShardingEnabled();

        CompletableFuture<Boolean> c = shardObserver.tryCreateShardAsync();

        assertNull(c);
        verify(shardMigrationExecutor, never()).executeAllOperations();
    }

    @Test
    void testShardSuccessful() throws ExecutionException, InterruptedException {
        doReturn(DEFAULT_MIN_ROLLBACK_HEIGHT).when(blockchainProcessor).getMinRollbackHeight();
        doReturn(true).when(heightConfig).isShardingEnabled();
        doReturn(DEFAULT_SHARDING_FREQUENCY).when(heightConfig).getShardingFrequency();
//        doReturn(new byte[]{1,2}).when(shardMigrationExecutor).calculateHash(DEFAULT_MIN_ROLLBACK_HEIGHT);

        boolean created = shardObserver.tryCreateShardAsync().get();

        assertTrue(created);
        verify(shardMigrationExecutor, times(1)).executeAllOperations();
    }

    @Test
    void testShardWhenShardExecutorThrowAnyException() throws ExecutionException, InterruptedException {
        doReturn(DEFAULT_MIN_ROLLBACK_HEIGHT).when(blockchainProcessor).getMinRollbackHeight();
        doReturn(true).when(heightConfig).isShardingEnabled();
        doReturn(DEFAULT_SHARDING_FREQUENCY).when(heightConfig).getShardingFrequency();
//        doReturn(new byte[]{1,2}).when(shardMigrationExecutor).calculateHash(DEFAULT_MIN_ROLLBACK_HEIGHT);
        doThrow(new RuntimeException()).when(shardMigrationExecutor).executeAllOperations();

        CompletableFuture<Boolean> c = shardObserver.tryCreateShardAsync();

        assertFalse(c.get());
        verify(shardMigrationExecutor, times(1)).executeAllOperations();
    }
}
