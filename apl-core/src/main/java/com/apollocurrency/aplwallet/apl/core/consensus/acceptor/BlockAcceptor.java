/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.consensus.acceptor;

import com.apollocurrency.aplwallet.apl.core.app.Block;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainProcessor;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionType;

import java.util.List;
import java.util.Map;

public interface BlockAcceptor {
    List<Transaction> accept(Block block, List<Transaction> validPhasedTransactions, List<Transaction> invalidPhasedTransactions,
                Map<TransactionType, Map<String, Integer>> duplicates) throws BlockchainProcessor.TransactionNotAcceptedException;
}