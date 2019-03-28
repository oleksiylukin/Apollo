/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.shard;

import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.COMPLETED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_COPIED_TO_SHARD;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_RELINKED_IN_MAIN;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_REMOVED_FROM_MAIN;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SECONDARY_INDEX_UPDATED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SHARD_SCHEMA_CREATED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SHARD_SCHEMA_FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.apollocurrency.aplwallet.apl.TemporaryFolderExtension;
import com.apollocurrency.aplwallet.apl.core.app.BlockImpl;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.app.EpochTime;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSyncImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.app.TrimService;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.chainid.HeightConfig;
import com.apollocurrency.aplwallet.apl.core.config.DaoConfig;
import com.apollocurrency.aplwallet.apl.core.config.PropertyProducer;
import com.apollocurrency.aplwallet.apl.core.db.BlockDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DbExtension;
import com.apollocurrency.aplwallet.apl.core.db.DerivedDbTablesRegistry;
import com.apollocurrency.aplwallet.apl.core.db.ShardAddConstraintsSchemaVersion;
import com.apollocurrency.aplwallet.apl.core.db.ShardInitTableSchemaVersion;
import com.apollocurrency.aplwallet.apl.core.db.ShardRecoveryDaoJdbcImpl;
import com.apollocurrency.aplwallet.apl.core.db.cdi.transaction.JdbiHandleFactory;
import com.apollocurrency.aplwallet.apl.core.db.dao.BlockIndexDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.ReferencedTransactionDao;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfig;
import com.apollocurrency.aplwallet.apl.core.db.dao.TransactionIndexDao;
import com.apollocurrency.aplwallet.apl.core.shard.commands.CopyDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.CreateShardSchemaCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.DeleteCopiedDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.FinishShardingCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.ReLinkDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.UpdateSecondaryIndexCommand;
import com.apollocurrency.aplwallet.apl.data.DbTestData;
import com.apollocurrency.aplwallet.apl.data.TransactionTestData;
import com.apollocurrency.aplwallet.apl.util.NtpTime;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import javax.inject.Inject;

@EnableWeld
class ShardMigrationExecutorTest {

    private static final String SHA_512 = "SHA-512";

    @RegisterExtension
    static TemporaryFolderExtension temporaryFolderExtension = new TemporaryFolderExtension();
    @RegisterExtension
    DbExtension extension = new DbExtension(DbTestData.getDbFileProperties(getTempFilePath("shardMigrationTestDb").toAbsolutePath().toString()));
    static PropertiesHolder propertiesHolder = initPropertyHolder();
    private static BlockchainConfig blockchainConfig = mock(BlockchainConfig.class);
    private static HeightConfig heightConfig = mock(HeightConfig.class);

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            BlockchainImpl.class, DaoConfig.class,
            JdbiHandleFactory.class, ReferencedTransactionDao.class,
            TransactionTestData.class, PropertyProducer.class,
            GlobalSyncImpl.class, BlockIndexDao.class, ShardingHashCalculatorImpl.class,
            DerivedDbTablesRegistry.class, DataTransferManagementReceiverImpl.class, ShardRecoveryDaoJdbcImpl.class,
            EpochTime.class, BlockDaoImpl.class, TransactionDaoImpl.class, TrimService.class, MigrateState.class,
            BlockImpl.class, ShardMigrationExecutor.class, FullTextConfig.class )
            .addBeans(MockBean.of(blockchainConfig, BlockchainConfig.class))
            .addBeans(MockBean.of(extension.getDatabaseManger(), DatabaseManager.class))
            .addBeans(MockBean.of(extension.getDatabaseManger().getJdbi(), Jdbi.class))
            .addBeans(MockBean.of(mock(TransactionProcessor.class), TransactionProcessor.class))
            .addBeans(MockBean.of(mock(NtpTime.class), NtpTime.class))
            .addBeans(MockBean.of(propertiesHolder, PropertiesHolder.class))
            .build();

    @Inject
    private JdbiHandleFactory jdbiHandleFactory;
    @Inject
    private DataTransferManagementReceiver managementReceiver;
    @Inject
    private ShardMigrationExecutor shardMigrationExecutor;
    @Inject
    private BlockIndexDao blockIndexDao;
    @Inject
    private TransactionIndexDao transactionIndexDao;

    @BeforeAll
    static void setUpAll() {
        Mockito.doReturn(SHA_512).when(heightConfig).getShardingDigestAlgorithm();
        Mockito.doReturn(heightConfig).when(blockchainConfig).getCurrentConfig();
    }

    @AfterEach
    void tearDown() {
        jdbiHandleFactory.close();
    }

    @Test
    void executeAllOperations() throws IOException {
        CreateShardSchemaCommand createShardSchemaCommand = new CreateShardSchemaCommand(managementReceiver,
                new ShardInitTableSchemaVersion());
        MigrateState state = shardMigrationExecutor.executeOperation(createShardSchemaCommand);
        assertEquals(SHARD_SCHEMA_CREATED, state);

        CopyDataCommand copyDataCommand = new CopyDataCommand(
                managementReceiver, 8000L);
        state = shardMigrationExecutor.executeOperation(copyDataCommand);
//        assertEquals(FAILED, state);
        assertEquals(DATA_COPIED_TO_SHARD, state);

        createShardSchemaCommand = new CreateShardSchemaCommand(managementReceiver,
                new ShardAddConstraintsSchemaVersion());
        state = shardMigrationExecutor.executeOperation(createShardSchemaCommand);
        assertEquals(SHARD_SCHEMA_FULL, state);

        ReLinkDataCommand reLinkDataCommand = new ReLinkDataCommand(managementReceiver, 8000L);
        state = shardMigrationExecutor.executeOperation(reLinkDataCommand);
        assertEquals(DATA_RELINKED_IN_MAIN, state);

        UpdateSecondaryIndexCommand updateSecondaryIndexCommand = new UpdateSecondaryIndexCommand(managementReceiver, 8000L);
        state = shardMigrationExecutor.executeOperation(updateSecondaryIndexCommand);
//        assertEquals(FAILED, state);
        assertEquals(SECONDARY_INDEX_UPDATED, state);

        // check by secondary indexes
        long blockIndexCount = blockIndexDao.countBlockIndexByShard(3L);
        assertEquals(8, blockIndexCount);
        long trIndexCount = transactionIndexDao.countTransactionIndexByShardId(3L);
        assertEquals(5, trIndexCount);

        DeleteCopiedDataCommand deleteCopiedDataCommand = new DeleteCopiedDataCommand(managementReceiver, 8000L);
        state = shardMigrationExecutor.executeOperation(deleteCopiedDataCommand);
//        assertEquals(FAILED, state);
        assertEquals(DATA_REMOVED_FROM_MAIN, state);

        FinishShardingCommand finishShardingCommand = new FinishShardingCommand(managementReceiver, new byte[]{3,4,5,6,1});
        state = shardMigrationExecutor.executeOperation(finishShardingCommand);
        assertEquals(COMPLETED, state);

    }

    @Test
    void executeAll() {
        shardMigrationExecutor.createAllCommands(8000);
        MigrateState state = shardMigrationExecutor.executeAllOperations();
//        assertEquals(FAILED, state);
        assertEquals(COMPLETED, state);
    }

    private Path getTempFilePath(String fileName) {
        try {
            return temporaryFolderExtension.newFolder().toPath().resolve(fileName);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to create shard db file", e);
        }
    }
    private static PropertiesHolder initPropertyHolder() {
        PropertiesHolder propertiesHolder = new PropertiesHolder();
        Properties properties = new Properties();
        properties.put("apl.trimFrequency", 1000);
        properties.put("apl.trimDerivedTables", true);
        properties.put("apl.maxRollback", 21600);

        propertiesHolder.init(properties);
        return propertiesHolder;

    }
}