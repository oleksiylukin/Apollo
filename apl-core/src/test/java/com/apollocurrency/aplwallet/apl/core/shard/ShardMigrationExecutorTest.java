/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.shard;

import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.COMPLETED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_COPIED_TO_SHARD;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_RELINKED_IN_MAIN;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.DATA_REMOVED_FROM_MAIN;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.MAIN_DB_BACKUPED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SECONDARY_INDEX_UPDATED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SHARD_SCHEMA_CREATED;
import static com.apollocurrency.aplwallet.apl.core.shard.MigrateState.SHARD_SCHEMA_FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.apollocurrency.aplwallet.apl.core.app.AplCoreRuntime;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.app.EpochTime;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSyncImpl;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.app.TransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.app.TrimService;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.chainid.HeightConfig;
import com.apollocurrency.aplwallet.apl.core.config.DaoConfig;
import com.apollocurrency.aplwallet.apl.core.config.PropertyProducer;
import com.apollocurrency.aplwallet.apl.core.db.BlockDao;
import com.apollocurrency.aplwallet.apl.core.db.BlockDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DerivedDbTablesRegistryImpl;
import com.apollocurrency.aplwallet.apl.core.db.ShardAddConstraintsSchemaVersion;
import com.apollocurrency.aplwallet.apl.core.db.ShardInitTableSchemaVersion;
import com.apollocurrency.aplwallet.apl.core.db.ShardRecoveryDaoJdbcImpl;
import com.apollocurrency.aplwallet.apl.core.db.TransactionalDataSource;
import com.apollocurrency.aplwallet.apl.core.db.cdi.transaction.JdbiHandleFactory;
import com.apollocurrency.aplwallet.apl.core.db.dao.BlockIndexDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.ReferencedTransactionDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.ShardDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.ShardRecoveryDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.TransactionIndexDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.Shard;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.ShardRecovery;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfigImpl;
import com.apollocurrency.aplwallet.apl.core.phasing.PhasingPollServiceImpl;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollLinkedTransactionTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollResultTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollVoterTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingVoteTable;
import com.apollocurrency.aplwallet.apl.core.shard.commands.BackupDbBeforeShardCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.CopyDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.CreateShardSchemaCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.DeleteCopiedDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.FinishShardingCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.ReLinkDataCommand;
import com.apollocurrency.aplwallet.apl.core.shard.commands.UpdateSecondaryIndexCommand;
import com.apollocurrency.aplwallet.apl.core.shard.hash.ShardHashCalculatorImpl;
import com.apollocurrency.aplwallet.apl.data.BlockTestData;
import com.apollocurrency.aplwallet.apl.data.DbTestData;
import com.apollocurrency.aplwallet.apl.data.TransactionTestData;
import com.apollocurrency.aplwallet.apl.extension.DbExtension;
import com.apollocurrency.aplwallet.apl.extension.TemporaryFolderExtension;
import com.apollocurrency.aplwallet.apl.util.NtpTime;
import com.apollocurrency.aplwallet.apl.util.env.UserMode;
import com.apollocurrency.aplwallet.apl.util.env.dirprovider.DirProvider;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
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
    WeldInitiator weld = WeldInitiator.from(
            BlockchainImpl.class, DaoConfig.class,
            JdbiHandleFactory.class, ReferencedTransactionDao.class,
            PropertyProducer.class,
            GlobalSyncImpl.class, BlockIndexDao.class, ShardHashCalculatorImpl.class,
            DerivedDbTablesRegistryImpl.class, DataTransferManagementReceiverImpl.class, ShardRecoveryDao.class,
            ShardRecoveryDaoJdbcImpl.class, ShardDao.class, ShardRecoveryDao.class,
            ExcludedTransactionDbIdExtractor.class,
            PhasingPollServiceImpl.class, FullTextConfigImpl.class,
            PhasingPollResultTable.class,
            PhasingPollTable.class,
            PhasingPollVoterTable.class,
            PhasingPollLinkedTransactionTable.class,
            PhasingVoteTable.class,
            EpochTime.class, BlockDaoImpl.class, TransactionDaoImpl.class, TrimService.class, ShardMigrationExecutor.class, FullTextConfigImpl.class )
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
    private Blockchain blockchain;
    @Inject
    private TransactionIndexDao transactionIndexDao;
    @Inject
    private BlockDao blockDao;
    @Inject
    private ShardDao shardDao;
    @Inject
    private ShardRecoveryDao recoveryDao;

    @BeforeAll
    static void setUpAll() {

        Mockito.doReturn(SHA_512).when(heightConfig).getShardingDigestAlgorithm();
        Mockito.doReturn(heightConfig).when(blockchainConfig).getCurrentConfig();
    }

    @BeforeEach
    void setUp() {
        blockchain.setLastBlock(new BlockTestData().BLOCK_11);
    }

    @AfterEach
    void tearDown() {
        jdbiHandleFactory.close();
        extension.getDatabaseManger().shutdown();
    }

    @Test
    void executeAllOperations() throws IOException {
        DirProvider dirProvider = mock(DirProvider.class);
        doReturn(temporaryFolderExtension.newFolder("backup").toPath()).when(dirProvider).getDbDir();
        AplCoreRuntime.getInstance().setup(new UserMode(), dirProvider);
        try {
            int snapshotBlockHeight = 8000;
            // prepare an save Recovery + new Shard info
            ShardRecovery recovery = new ShardRecovery(MigrateState.INIT);
            recoveryDao.saveShardRecovery(recovery);
            Shard newShard = new Shard(snapshotBlockHeight);
            shardDao.saveShard(newShard);

            MigrateState state;

            BackupDbBeforeShardCommand beforeShardCommand = new BackupDbBeforeShardCommand(managementReceiver);
            state = shardMigrationExecutor.executeOperation(beforeShardCommand);
            assertEquals(MAIN_DB_BACKUPED, state);
            assertTrue(Files.exists(dirProvider.getDbDir().resolve("BACKUP-BEFORE-apl-blockchain-shard-0000004.zip")));

            CreateShardSchemaCommand createShardSchemaCommand = new CreateShardSchemaCommand(managementReceiver,
                    new ShardInitTableSchemaVersion());
            state = shardMigrationExecutor.executeOperation(createShardSchemaCommand);
            assertEquals(SHARD_SCHEMA_CREATED, state);

            TransactionTestData td = new TransactionTestData();
            Set<Long> dbIds = new HashSet<>();
            dbIds.add(td.DB_ID_6);
            dbIds.add(td.DB_ID_10);
            CopyDataCommand copyDataCommand = new CopyDataCommand(
                    managementReceiver, snapshotBlockHeight, dbIds);
            state = shardMigrationExecutor.executeOperation(copyDataCommand);
//        assertEquals(FAILED, state);
            assertEquals(DATA_COPIED_TO_SHARD, state);

            TransactionalDataSource shardDataSource = ((ShardManagement) extension.getDatabaseManger()).getOrCreateShardDataSourceById(4L);
            long count = blockDao.getBlockCount(shardDataSource, 0, (int) snapshotBlockHeight);
            assertEquals(8, count);

            createShardSchemaCommand = new CreateShardSchemaCommand(managementReceiver,
                    new ShardAddConstraintsSchemaVersion());
            state = shardMigrationExecutor.executeOperation(createShardSchemaCommand);
            assertEquals(SHARD_SCHEMA_FULL, state);

            ReLinkDataCommand reLinkDataCommand = new ReLinkDataCommand(managementReceiver, snapshotBlockHeight, dbIds);
            state = shardMigrationExecutor.executeOperation(reLinkDataCommand);
            assertEquals(DATA_RELINKED_IN_MAIN, state);

            UpdateSecondaryIndexCommand updateSecondaryIndexCommand = new UpdateSecondaryIndexCommand(managementReceiver, snapshotBlockHeight, dbIds);
            state = shardMigrationExecutor.executeOperation(updateSecondaryIndexCommand);
//        assertEquals(FAILED, state);
            assertEquals(SECONDARY_INDEX_UPDATED, state);

            // check by secondary indexes
            long blockIndexCount = blockIndexDao.countBlockIndexByShard(4L);
            assertEquals(8, blockIndexCount);
            long trIndexCount = transactionIndexDao.countTransactionIndexByShardId(4L);
            assertEquals(6, trIndexCount);

            Transaction tx = blockchain.getTransaction(td.TRANSACTION_0.getId());

            assertEquals(td.TRANSACTION_0, tx); // check that transaction was ignored

            DeleteCopiedDataCommand deleteCopiedDataCommand = new DeleteCopiedDataCommand(managementReceiver, snapshotBlockHeight);
            state = shardMigrationExecutor.executeOperation(deleteCopiedDataCommand);
//        assertEquals(FAILED, state);
            assertEquals(DATA_REMOVED_FROM_MAIN, state);

            count = blockDao.getBlockCount((int) snapshotBlockHeight, 105000);
            assertEquals(5, count);

            byte[] shardHash = "000000000".getBytes();
            FinishShardingCommand finishShardingCommand = new FinishShardingCommand(managementReceiver, shardHash);
            state = shardMigrationExecutor.executeOperation(finishShardingCommand);
            assertEquals(COMPLETED, state);
        } finally {
            AplCoreRuntime.getInstance().setup(null, null); //remove when AplCoreRuntime become an injectable bean
        }
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