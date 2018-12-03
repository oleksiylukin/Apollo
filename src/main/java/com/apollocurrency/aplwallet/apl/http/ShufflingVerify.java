/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.http;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import com.apollocurrency.aplwallet.apl.Account;
import com.apollocurrency.aplwallet.apl.AplException;
import com.apollocurrency.aplwallet.apl.Attachment;
import com.apollocurrency.aplwallet.apl.Shuffling;

public final class ShufflingVerify extends CreateTransaction {

    private static class ShufflingVerifyHolder {
        private static final ShufflingVerify INSTANCE = new ShufflingVerify();
    }

    public static ShufflingVerify getInstance() {
        return ShufflingVerifyHolder.INSTANCE;
    }

    private ShufflingVerify() {
        super(new APITag[] {APITag.SHUFFLING, APITag.CREATE_TRANSACTION}, "shuffling", "shufflingStateHash");
    }

    @Override
    protected CreateTransactionRequestData parseRequest(HttpServletRequest req, boolean validate) throws AplException {
        Shuffling shuffling = ParameterParser.getShuffling(req);
        byte[] shufflingStateHash = ParameterParser.getBytes(req, "shufflingStateHash", validate);
        if (!Arrays.equals(shufflingStateHash, shuffling.getStateHash())) {
            throw new ParameterException(JSONResponses.incorrect("shufflingStateHash", "Shuffling is in a different state now"));
        }
        Attachment attachment = new Attachment.ShufflingVerification(shuffling.getId(), shufflingStateHash);

        Account account = ParameterParser.getSenderAccount(req, validate);
        return new CreateTransactionRequestData(attachment, account);
    }
}
