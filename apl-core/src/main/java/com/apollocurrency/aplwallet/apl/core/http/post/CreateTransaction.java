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
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.http.post;

import com.apollocurrency.aplwallet.apl.core.account.Account;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.EpochTime;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.http.APITag;
import com.apollocurrency.aplwallet.apl.core.http.AbstractAPIRequestHandler;
import com.apollocurrency.aplwallet.apl.core.http.JSONData;
import com.apollocurrency.aplwallet.apl.core.http.JSONResponses;
import com.apollocurrency.aplwallet.apl.core.http.ParameterException;
import com.apollocurrency.aplwallet.apl.core.http.ParameterParser;
import com.apollocurrency.aplwallet.apl.core.phasing.model.PhasingParams;
import com.apollocurrency.aplwallet.apl.core.transaction.FeeCalculator;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionValidator;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.Attachment;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.EncryptToSelfMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.EncryptedMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.MessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PhasingAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PhasingAppendixV2;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PrunableEncryptedMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PrunablePlainMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PublicKeyAnnouncementAppendix;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.util.AplException;
import com.apollocurrency.aplwallet.apl.util.Constants;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.FEATURE_NOT_AVAILABLE;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.INCORRECT_DEADLINE;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.INCORRECT_EC_BLOCK;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.INCORRECT_LINKED_FULL_HASH;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.INCORRECT_WHITELIST;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.MISSING_DEADLINE;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.MISSING_SECRET_PHRASE;
import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.NOT_ENOUGH_FUNDS;

public abstract class CreateTransaction extends AbstractAPIRequestHandler {
    private static TransactionValidator validator = CDI.current().select(TransactionValidator.class).get();
    private static PropertiesHolder propertiesHolder = CDI.current().select(PropertiesHolder.class).get();
    private static Blockchain blockchain = CDI.current().select(Blockchain.class).get();
    protected EpochTime timeService = CDI.current().select(EpochTime.class).get();
    private static FeeCalculator feeCalculator = CDI.current().select(FeeCalculator.class).get();
    private static final String[] commonParameters = new String[]{"secretPhrase", "publicKey", "feeATM",
            "deadline", "referencedTransactionFullHash", "broadcast",
            "message", "messageIsText", "messageIsPrunable",
            "messageToEncrypt", "messageToEncryptIsText", "encryptedMessageData", "encryptedMessageNonce", "encryptedMessageIsPrunable", "compressMessageToEncrypt",
            "messageToEncryptToSelf", "messageToEncryptToSelfIsText", "encryptToSelfMessageData", "encryptToSelfMessageNonce", "compressMessageToEncryptToSelf",
            "phased", "phasingFinishHeight", "phasingVotingModel", "phasingQuorum", "phasingMinBalance", "phasingHolding", "phasingMinBalanceModel",
            "phasingWhitelisted", "phasingWhitelisted", "phasingWhitelisted",
            "phasingLinkedFullHash", "phasingLinkedFullHash", "phasingLinkedFullHash",
            "phasingHashedSecret", "phasingHashedSecretAlgorithm",
            "recipientPublicKey",
            "ecBlockId", "ecBlockHeight"};

    private static String[] addCommonParameters(String[] parameters) {
        String[] result = Arrays.copyOf(parameters, parameters.length + commonParameters.length);
        System.arraycopy(commonParameters, 0, result, parameters.length, commonParameters.length);
        return result;
    }

    public CreateTransaction(APITag[] apiTags, String... parameters) {
        super(apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    public CreateTransaction(String fileParameter, APITag[] apiTags, String... parameters) {
        super(fileParameter, apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    public JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, Attachment attachment)
            throws AplException {
        return createTransaction(req, senderAccount, 0, 0, attachment);
    }

    public JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId, long amountATM)
            throws AplException {
        return createTransaction(req, senderAccount, recipientId, amountATM, Attachment.ORDINARY_PAYMENT);
    }
    public JSONStreamAware createPrivateTransaction(HttpServletRequest req, Account senderAccount, long recipientId, long amountATM)
            throws AplException {
        return createTransaction(req, senderAccount, recipientId, amountATM, Attachment.PRIVATE_PAYMENT);
    }

    public PhasingAppendixV2 parsePhasing(HttpServletRequest req) throws ParameterException {
        Blockchain blockchain = lookupBlockchain();

        int phasingTimeLockDuration = -1;
        int phasingFinishHeight = ParameterParser.getInt(req, "phasingFinishHeight",
                -1, blockchain.getHeight() + Constants.MAX_PHASING_DURATION + 1, true);

        if(req.getParameter("phasingFinishTime") != null){
            phasingTimeLockDuration = ParameterParser.getInt(req, "phasingFinishTime",
                    -1, Constants.MAX_PHASING_TIME_DURATION_SEC, false);
        }

        if(phasingFinishHeight != -1 && phasingTimeLockDuration != -1){
            throw new ParameterException(
                    JSONResponses.incorrect("Only one parameter should be filled 'phasingFinishHeight or phasingFinishTime'"));
        }

        int phasingFinishTime = -1;
        if(phasingTimeLockDuration == -1) {
            phasingFinishHeight = ParameterParser.getInt(req, "phasingFinishHeight",
                    blockchain.getHeight() + 1,
                    blockchain.getHeight() + Constants.MAX_PHASING_DURATION + 1,
                    true);
        } else {
            phasingFinishTime = timeService.getEpochTime() + phasingTimeLockDuration;
        }

        PhasingParams phasingParams = parsePhasingParams(req, "phasing");
        
        byte[][] linkedFullHashes = null;
        String[] linkedFullHashesValues = req.getParameterValues("phasingLinkedFullHash");
        if (linkedFullHashesValues != null && linkedFullHashesValues.length > 0) {
            linkedFullHashes = new byte[linkedFullHashesValues.length][];
            for (int i = 0; i < linkedFullHashes.length; i++) {
                linkedFullHashes[i] = Convert.parseHexString(linkedFullHashesValues[i]);
                if (Convert.emptyToNull(linkedFullHashes[i]) == null || linkedFullHashes[i].length != 32) {
                    throw new ParameterException(INCORRECT_LINKED_FULL_HASH);
                }
            }
        }

        byte[] hashedSecret = Convert.parseHexString(Convert.emptyToNull(req.getParameter("phasingHashedSecret")));
        byte algorithm = ParameterParser.getByte(req, "phasingHashedSecretAlgorithm", (byte) 0, Byte.MAX_VALUE, false);

        return new PhasingAppendixV2(phasingFinishHeight, phasingFinishTime, phasingParams, linkedFullHashes, hashedSecret, algorithm);
    }

    public PhasingParams parsePhasingParams(HttpServletRequest req, String parameterPrefix) throws ParameterException {
        byte votingModel = ParameterParser.getByte(req, parameterPrefix + "VotingModel", (byte)-1, (byte)5, true);
        long quorum = ParameterParser.getLong(req, parameterPrefix + "Quorum", 0, Long.MAX_VALUE, false);
        long minBalance = ParameterParser.getLong(req, parameterPrefix + "MinBalance", 0, Long.MAX_VALUE, false);
        byte minBalanceModel = ParameterParser.getByte(req, parameterPrefix + "MinBalanceModel", (byte)0, (byte)3, false);
        long holdingId = ParameterParser.getUnsignedLong(req, parameterPrefix + "Holding", false);
        long[] whitelist = null;
        String[] whitelistValues = req.getParameterValues(parameterPrefix + "Whitelisted");
        if (whitelistValues != null && whitelistValues.length > 0) {
            whitelist = new long[whitelistValues.length];
            for (int i = 0; i < whitelistValues.length; i++) {
                whitelist[i] = Convert.parseAccountId(whitelistValues[i]);
                if (whitelist[i] == 0) {
                    throw new ParameterException(INCORRECT_WHITELIST);
                }
            }
        }
        return new PhasingParams(votingModel, holdingId, quorum, minBalance, minBalanceModel, whitelist);
    }

    public JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId,
                                            long amountATM, Attachment attachment) throws AplException.ValidationException, ParameterException {
        String deadlineValue = req.getParameter("deadline");
        String referencedTransactionFullHash = Convert.emptyToNull(req.getParameter("referencedTransactionFullHash"));
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        String publicKeyValue = Convert.emptyToNull(req.getParameter("publicKey"));
        String passphrase = Convert.emptyToNull(ParameterParser.getPassphrase(req, false));
        boolean broadcast = !"false".equalsIgnoreCase(req.getParameter("broadcast")) && (secretPhrase != null || passphrase != null);
        EncryptedMessageAppendix encryptedMessage = null;
        PrunableEncryptedMessageAppendix prunableEncryptedMessage = null;
        if (attachment.getTransactionType().canHaveRecipient() && recipientId != 0) {
            Account recipient = Account.getAccount(recipientId);
            if ("true".equalsIgnoreCase(req.getParameter("encryptedMessageIsPrunable"))) {
                prunableEncryptedMessage = (PrunableEncryptedMessageAppendix) ParameterParser.getEncryptedMessage(req, recipient,
                        senderAccount.getId(),true);
            } else {
                encryptedMessage = (EncryptedMessageAppendix) ParameterParser.getEncryptedMessage(req, recipient, senderAccount.getId(), false);
            }
        }
        EncryptToSelfMessageAppendix encryptToSelfMessage = ParameterParser.getEncryptToSelfMessage(req, senderAccount.getId());
        MessageAppendix message = null;
        PrunablePlainMessageAppendix prunablePlainMessage = null;
        if ("true".equalsIgnoreCase(req.getParameter("messageIsPrunable"))) {
            prunablePlainMessage = (PrunablePlainMessageAppendix) ParameterParser.getPlainMessage(req, true);
        } else {
            message = (MessageAppendix) ParameterParser.getPlainMessage(req, false);
        }
        PublicKeyAnnouncementAppendix publicKeyAnnouncement = null;
        String recipientPublicKey = Convert.emptyToNull(req.getParameter("recipientPublicKey"));
        if (recipientPublicKey != null) {
            publicKeyAnnouncement = new PublicKeyAnnouncementAppendix(Convert.parseHexString(recipientPublicKey));
        }

        PhasingAppendix phasing = null;
        boolean phased = "true".equalsIgnoreCase(req.getParameter("phased"));
        if (phased) {
            phasing = parsePhasing(req);
        }

        if (secretPhrase == null && publicKeyValue == null && passphrase == null) {
            return MISSING_SECRET_PHRASE;
        } else if (deadlineValue == null) {
            return MISSING_DEADLINE;
        }

        short deadline;
        try {
            deadline = Short.parseShort(deadlineValue);
            if (deadline < 1) {
                return INCORRECT_DEADLINE;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_DEADLINE;
        }

        long feeATM = ParameterParser.getFeeATM(req);
        int ecBlockHeight = ParameterParser.getInt(req, "ecBlockHeight", 0, Integer.MAX_VALUE, false);
        long ecBlockId = Convert.parseUnsignedLong(req.getParameter("ecBlockId"));
        Blockchain blockchain = lookupBlockchain();
        if (ecBlockId != 0 && ecBlockId != blockchain.getBlockIdAtHeight(ecBlockHeight)) {
            return INCORRECT_EC_BLOCK;
        }
        if (ecBlockId == 0 && ecBlockHeight > 0) {
            ecBlockId = blockchain.getBlockIdAtHeight(ecBlockHeight);
        }

        JSONObject response = new JSONObject();

        // shouldn't try to get publicKey from senderAccount as it may have not been set yet
        byte[] publicKey = ParameterParser.getPublicKey(req, senderAccount.getId());
        int timestamp = timeService.getEpochTime();
        try {
            Transaction.Builder builder = Transaction.newTransactionBuilder(publicKey, amountATM, feeATM,
                    deadline, attachment, timestamp).referencedTransactionFullHash(referencedTransactionFullHash);
            if (attachment.getTransactionType().canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            builder.appendix(encryptedMessage);
            builder.appendix(message);
            builder.appendix(publicKeyAnnouncement);
            builder.appendix(encryptToSelfMessage);
            builder.appendix(phasing);
            builder.appendix(prunablePlainMessage);
            builder.appendix(prunableEncryptedMessage);
            if (ecBlockId != 0) {
                builder.ecBlockId(ecBlockId);
                builder.ecBlockHeight(ecBlockHeight);
            }
            byte[] keySeed = ParameterParser.getKeySeed(req, senderAccount.getId(), false);
            Transaction transaction = builder.build(keySeed);
            if (feeATM <= 0 || (propertiesHolder.correctInvalidFees() && keySeed == null)) {
                int effectiveHeight = blockchain.getHeight();
                long minFee = feeCalculator.getMinimumFeeATM(transaction, effectiveHeight);
                feeATM = Math.max(minFee, feeATM);
                transaction.setFeeATM(feeATM);
            }
            try {
                if (Math.addExact(amountATM, transaction.getFeeATM()) > senderAccount.getUnconfirmedBalanceATM()) {
                    return NOT_ENOUGH_FUNDS;
                }
            } catch (ArithmeticException e) {
                return NOT_ENOUGH_FUNDS;
            }

            JSONObject transactionJSON = JSONData.unconfirmedTransaction(transaction);
            response.put("transactionJSON", transactionJSON);
            try {
                response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
            } catch (AplException.NotYetEncryptedException ignore) {}
            if (keySeed != null) {
                response.put("transaction", transaction.getStringId());
                response.put("fullHash", transactionJSON.get("fullHash"));
                response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
                response.put("signatureHash", transactionJSON.get("signatureHash"));
            }
            if (broadcast) {
                lookupTransactionProcessor().broadcast(transaction);
                response.put("broadcasted", true);
            } else {
                validator.validate(transaction);
                response.put("broadcasted", false);
            }
        } catch (AplException.NotYetEnabledException e) {
            return FEATURE_NOT_AVAILABLE;
        } catch (AplException.InsufficientBalanceException e) {
            throw e;
        } catch (AplException.ValidationException e) {
            if (broadcast) {
                response.clear();
            }
            response.put("broadcasted", false);
            JSONData.putException(response, e);
        }
        return response;

    }
    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected String vaultAccountName() {
        return "sender";
    }

    @Override
    protected boolean is2FAProtected() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
