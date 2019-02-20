/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.consensus.forging;


import com.apollocurrency.aplwallet.apl.core.app.Block;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainProcessor;
import com.apollocurrency.aplwallet.apl.core.app.Time;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.config.Property;
import com.apollocurrency.aplwallet.apl.core.consensus.ConsensusFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class BlockGeneratorImpl implements BlockGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(BlockGeneratorImpl.class);
    // All available generators
    private Map<Long, Generator> generators = new HashMap<>();
    // Filtered generators which have positive effective balance
    // This field cached for better performance
    private List<Generator> sortedGenerators;
    // Last block id, calculated during generation iteration
    // This field cached for better performance
    private long lastBlockId;
    private Blockchain blockchain;
    private BlockchainProcessor blockchainProcessor;
    private BlockchainConfig blockchainConfig;
    private Time time;
    private boolean suspendGeneration;
    private ConsensusFacade consensusFacade;
    private final int defaultGenerationDelay;
    private final int maxNumberOfGenerators;
    private int generationDelay;

    @Inject
    public BlockGeneratorImpl(Blockchain blockchain,
                              BlockchainProcessor blockchainProcessor,
                              BlockchainConfig blockchainConfig,
                              Time time,
                              ConsensusFacade consensusFacade,
                              @Property(name = "apl.forgingDelay") int defaultGenerationDelay,
                              @Property(name = "apl.maxNumberOfForgers") int maxNumberOfGenerators

                              ) {
        this.blockchain = blockchain;
        this.blockchainProcessor = blockchainProcessor;
        this.blockchainConfig = blockchainConfig;
        this.time = time;
        this.defaultGenerationDelay = defaultGenerationDelay;
        this.generationDelay = defaultGenerationDelay;
        this.maxNumberOfGenerators = maxNumberOfGenerators;
        this.consensusFacade = consensusFacade;
    }

    @Override
    public void run() {
        performGenerationIteration();
    }

    @Override
    public void performGenerationIteration() {
        if (suspendGeneration) {
            return;
        }
        try {
            try {
                blockchain.updateLock();
                try {
                    Block lastBlock = blockchain.getLastBlock();
                    if (lastBlock == null || lastBlock.getHeight() < blockchainConfig.getLastKnownBlockHeight()) {
                        return;
                    }
//                    Note, that generation delay can be negative, so generation will be more faster
                    final int generationLimit = time.getTime() - generationDelay;
                    if (lastBlock.getId() != lastBlockId || sortedGenerators == null) {
                        lastBlockId = lastBlock.getId();
                        if (lastBlock.getTimestamp() > time.getTime() - 600) {
                            Block previousBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
                            for (Generator generator : generators.values()) {
                                consensusFacade.updateGeneratorData(generator, previousBlock);
                                int timestamp = (int) (generator.getHitTime() + 1);
                                if (generator.getHitTime() > 0 && timestamp < lastBlock.getTimestamp() - lastBlock.getTimeout()) {
                                    LOG.debug("Pop off: " + generator.toString() + " will pop off last block " + lastBlock.getStringId());
                                    blockchainProcessor.popOffAndProcessTransactions(previousBlock);
                                    lastBlock = previousBlock;
                                    lastBlockId = previousBlock.getId();
                                    break;
                                }
                            }
                        }
                        List<Generator> activeGenerators = new ArrayList<>();
                        for (Generator generator : generators.values()) {
                            consensusFacade.updateGeneratorData(generator, lastBlock);
                            if (generator.getEffectiveBalance().signum() > 0) {
                                activeGenerators.add(generator);
                            }
                        }
                        Collections.sort(activeGenerators);
                        sortedGenerators = Collections.unmodifiableList(activeGenerators);
                    }
                    for (Generator generator : sortedGenerators) {
                        tryGenerateBlock(generator, lastBlock, generationLimit);
                    }
                } finally {
                    blockchain.updateUnlock();
                }
            } catch (Exception e) {
                LOG.info("Error in block generation thread", e);
            }
        } catch (Throwable t) {
            LOG.error("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS." + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    }

    private void tryGenerateBlock(Generator generator, Block lastBlock, int generationLimit) throws BlockchainProcessor.BlockNotAcceptedException {
        Block block = consensusFacade.generateBlock(generator, lastBlock, generationLimit);
        boolean tryGenerate = block != null;
        int start = time.getTime();
        while (tryGenerate) {
            try {
                blockchainProcessor.trySaveGeneratedBlock(block);
                setGenerationDelay(defaultGenerationDelay);
                tryGenerate = false;
            }
            catch (BlockchainProcessor.TransactionNotAcceptedException e) {
                // assume that invalid transaction was removed, so we try for next 2s to generate correct block
                if (time.getTime() - start > 2) {
                    throw e;
                }
                // create new block
                block = consensusFacade.generateBlock(generator, lastBlock, generationLimit);
            }
        }
    }



    @Override
    public void setGenerationDelay(int generationDelay) {
        this.generationDelay = generationDelay;
    }

    @Override
    public boolean canGenerateBetterBlock(long prevBlockId, Block anotherBlock) {
        blockchain.readLock();
        try {
            if (prevBlockId == lastBlockId && sortedGenerators != null) {
                for (Generator generator : sortedGenerators) {
                    if (generator.getHitTime()  + 1 < anotherBlock.getTimestamp() - anotherBlock.getTimeout()) {
                        return true;
                    }
                }
            }
        } finally {
            blockchain.readUnlock();
        }
        return false;
    }

    @Override
    public Generator startGeneration(Generator generator) {
        if (generators.size() >= maxNumberOfGenerators) {
            throw new RuntimeException("Cannot generate blocks with more than " + maxNumberOfGenerators + " accounts on the same node");
        }
        Generator old = generators.putIfAbsent(generator.getAccountId(), generator);
        if (old != null) {
            LOG.debug(old + " is already generate block");
            return old;
        }
        LOG.debug(generator + " started");
        return generator;
    }

    @Override
    public Generator stopGeneration(Generator generator) {
        Generator oldGenerator = generators.remove(generator.getAccountId());
        if (oldGenerator != null) {
            blockchain.updateLock();
            try {
                sortedGenerators = null;
            } finally {
                blockchain.updateUnlock();
            }
            LOG.debug(generator + " stopped");
        }
        return generator;
    }

    @Override
    public void suspendAll() {
        suspendGeneration = true;
    }

    @Override
    public void resumeAll() {
        suspendGeneration = false;
    }

    @Override
    public int stopAll() {
        int count = generators.size();
        Iterator<Generator> iter = generators.values().iterator();
        while (iter.hasNext()) {
            Generator generator = iter.next();
            iter.remove();
            LOG.debug(generator + " stopped");
        }
        blockchain.updateLock();
        try {
            sortedGenerators = null;
        } finally {
            blockchain.updateUnlock();
        }
        return count;
    }

    @Override
    public Generator getGenerator(long id) {
        return generators.get(id);
    }

    @Override
    public Collection<Generator> getAllGenerators() {
        return Collections.unmodifiableCollection(generators.values());
    }
}
