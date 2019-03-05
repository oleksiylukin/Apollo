/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.util.env.config;

import com.apollocurrency.aplwallet.apl.util.JSON;
import com.apollocurrency.aplwallet.apl.util.env.dirprovider.ConfigDirProvider;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChainsConfigLoaderTest {
    private static UUID chainId = UUID.fromString("3fecf3bd-86a3-436b-a1d6-41eefc0bd1c6");
    private static final List<BlockchainProperties> BLOCKCHAIN_PROPERTIES = Arrays.asList(
        new BlockchainProperties(0, 255, 60, 67, 53, 30000000000L),
            new BlockchainProperties(2000, 300, 2, 4, 1,  30000000000L, new ConsensusSettings(ConsensusSettings.Type.POS,
                    new AdaptiveForgingSettings(true, 60, 0))),
            new BlockchainProperties(42300, 300, 2, 4, 1, 30000000000L, new ShardingSettings(true), new ConsensusSettings(new AdaptiveForgingSettings(true, 10, 0))),
            new BlockchainProperties(100000, 300, 2, 4, 1, 30000000000L, new ShardingSettings(true, 1_000_000),
                    new ConsensusSettings(new AdaptiveForgingSettings(true, 10, 0)))
    );

    private static final Chain EXPECTED_CHAIN = new Chain(chainId, true, Collections.emptyList(), Arrays.asList("51.15.250.32",
            "51.15.253.171",
            "51.15.210.116",
            "51.15.242.197",
            "51.15.218.241"),
            Collections.emptyList(),
            "Apollo experimental testnet",
            "NOT STABLE testnet for experiments. Don't use it if you don't know what is it", "Apollo",
            "APL", "Apollo", "data/genesisAccounts-testnet.json", BLOCKCHAIN_PROPERTIES);

    private static final String CONFIG_NAME = "test-chains.json";

    @Rule
    private static TemporaryFolder folder = new TemporaryFolder();


    @BeforeAll
    public static void init() throws IOException {
        folder.create();
    }
    @AfterAll
    public static void shutdown() throws IOException {
        folder.delete();
    }
    @Test
    public void testLoadConfig() {
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(CONFIG_NAME);
        Map<UUID, Chain> chains = chainsConfigLoader.load();
        Assertions.assertEquals(1, chains.size());
        Chain actualChain = chains.get(chainId);
        Assertions.assertNotNull(actualChain);
        Assertions.assertEquals(EXPECTED_CHAIN, actualChain);
    }

    @Test
    void testLoadAndSaveConfig() throws IOException {
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(CONFIG_NAME);
        Map<UUID, Chain> loadedChains = chainsConfigLoader.load();
        Assertions.assertEquals(1, loadedChains.size());
        File file = folder.newFile();
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(file, loadedChains.values());
        chainsConfigLoader = new ChainsConfigLoader(true, folder.getRoot().getPath(), file.getName());
        Map<UUID, Chain> reloadedChains = chainsConfigLoader.load();
        Assertions.assertEquals(loadedChains, reloadedChains);
    }

    @Test
    void testLoadTwoConfigs() throws IOException {
        Chain secondChain = EXPECTED_CHAIN.copy();
        UUID secondChainId = UUID.randomUUID();
        secondChain.setChainId(secondChainId);
        Chain thirdChain = EXPECTED_CHAIN.copy();
        thirdChain.getBlockchainPropertiesList().get(0).setBlockTime(2);
        thirdChain.getBlockchainPropertiesList().get(0).setMaxBalance(0);
        List<Chain> chainsToWrite = Arrays.asList(secondChain, thirdChain);

        Path userConfigFile = folder.getRoot().toPath().resolve(CONFIG_NAME);
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(userConfigFile.toFile(), chainsToWrite);
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(false, folder.getRoot().getPath(), CONFIG_NAME);
        Map<UUID, Chain> actualChains = chainsConfigLoader.load();
        Assertions.assertEquals(2, actualChains.size());
        Map<UUID, Chain> expectedChains = chainsToWrite.stream().collect(Collectors.toMap(Chain::getChainId, Function.identity()));
        Assertions.assertEquals(expectedChains, actualChains);
    }
    @Test
    void testLoadFromUserDefinedLocation() throws IOException {
        Chain secondChain = EXPECTED_CHAIN.copy();
        UUID secondChainId = UUID.randomUUID();
        secondChain.setChainId(secondChainId);
        List<Chain> chainsToWrite = Arrays.asList(secondChain);

        Path userConfigFile = folder.getRoot().toPath().resolve(CONFIG_NAME);
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(userConfigFile.toFile(), chainsToWrite);
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(true, folder.getRoot().getPath(), CONFIG_NAME);
        Map<UUID, Chain> actualChains = chainsConfigLoader.load();
        Assertions.assertEquals(1, actualChains.size());
        Map<UUID, Chain> expectedChains = chainsToWrite.stream().collect(Collectors.toMap(Chain::getChainId, Function.identity()));
        Assertions.assertEquals(expectedChains, actualChains);
    }
    @Test
    void testLoadConfigWhichWasNotFound() throws IOException {
        String wrongFileName = CONFIG_NAME + ".wrongName";
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(false, folder.getRoot().getPath(), wrongFileName);
        Map<UUID, Chain> actualChains = chainsConfigLoader.load();
        Assertions.assertNull(actualChains);
    }

    @Test
    void testLoadConfigUsingConfigDirProvider() throws IOException {
        ConfigDirProvider configDirProvider = Mockito.mock(ConfigDirProvider.class);
        File installationFolder = folder.newFolder();
        File userConfigFolder = folder.newFolder();
        File sysConfigDir = folder.newFolder();
        Mockito.doReturn(installationFolder.getPath()).when(configDirProvider).getInstallationConfigDirectory();
        Mockito.doReturn(sysConfigDir.getPath()).when(configDirProvider).getSysConfigDirectory();
        Mockito.doReturn(userConfigFolder.getPath()).when(configDirProvider).getUserConfigDirectory();
        Chain chain1 = EXPECTED_CHAIN.copy();
        chain1.setChainId(UUID.randomUUID());
        Chain chain2 = EXPECTED_CHAIN.copy();
        chain2.getBlockchainPropertiesList().get(0).setBlockTime(2);
        chain2.getBlockchainPropertiesList().get(0).setMaxBalance(0);
        Chain chain3 = chain2.copy();
        chain3.getBlockchainPropertiesList().get(1).setMaxNumberOfTransactions(400);
        Chain chain4 = chain1.copy();
        chain4.setActive(true);
        Chain chain5 = EXPECTED_CHAIN.copy();
        chain5.setChainId(UUID.randomUUID());
        Chain chain6 = chain5.copy();
        chain6.setDescription("Another description");
        List<Chain> chainsToWriteToUserConfigDir = Arrays.asList(chain6, chain3);
        List<Chain> chainsToWriteToInstallationDir = Arrays.asList(chain5, chain4);
        List<Chain> chainsToWriteToSysConfigDir = Arrays.asList(chain1, chain2);

        Path userConfigFile = userConfigFolder.toPath().resolve(CONFIG_NAME);
        Path sysConfigFile = sysConfigDir.toPath().resolve(CONFIG_NAME);
        Path installationConfigFile = installationFolder.toPath().resolve(CONFIG_NAME);
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(userConfigFile.toFile(), chainsToWriteToUserConfigDir);
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(sysConfigFile.toFile(), chainsToWriteToSysConfigDir);
        JSON.getMapper().writerWithDefaultPrettyPrinter().writeValue(installationConfigFile.toFile(), chainsToWriteToInstallationDir);

        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(configDirProvider, false, CONFIG_NAME);
        Map<UUID, Chain> actualChains = chainsConfigLoader.load();
        Assertions.assertEquals(3, actualChains.size());
        Map<UUID, Chain> expectedChains = Stream.of(chain4, chain3, chain6).collect(Collectors.toMap(Chain::getChainId,
                Function.identity()));
        Assertions.assertEquals(expectedChains, actualChains);
    }

    @Test
    void testLoadConfigWhenUserConfigAndResourcesIgnored() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainsConfigLoader(true, null, CONFIG_NAME));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainsConfigLoader(true, null));

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainsConfigLoader(null, true));
    }

    @Test
    void testLoadConfigWhenJsonIsIncorrect() throws IOException {

        Path userConfigFile = folder.newFolder().toPath().resolve(CONFIG_NAME);
        Files.createFile(userConfigFile);
        ChainsConfigLoader chainsConfigLoader = new ChainsConfigLoader(false, userConfigFile.getParent().toString(), CONFIG_NAME);
        Map<UUID, Chain> chains = chainsConfigLoader.load();
        Assertions.assertEquals(EXPECTED_CHAIN, chains.get(EXPECTED_CHAIN.getChainId()));
    }

}