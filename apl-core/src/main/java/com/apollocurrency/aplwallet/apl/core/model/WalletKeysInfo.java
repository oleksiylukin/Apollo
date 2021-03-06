/*
* Copyright © 2019 Apollo Foundation
*/

package com.apollocurrency.aplwallet.apl.core.model;

import com.apollocurrency.aplwallet.apl.core.app.Convert2;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.eth.model.EthWalletKey;
import com.apollocurrency.aplwallet.apl.util.StringUtils;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WalletKeysInfo {

    private AplWalletKey aplWalletKey;
    private EthWalletKey ethWalletKey;
    private EthWalletKey paxWalletKey;
    private String passphrase;

    public WalletKeysInfo(ApolloFbWallet apolloWallet, String passphrase) {
        this.aplWalletKey = apolloWallet.getAplWalletKey();
        this.ethWalletKey = apolloWallet.getEthWalletKey();
        this.paxWalletKey = apolloWallet.getPaxWalletKey();
        this.passphrase = passphrase;
    }

    public AplWalletKey getAplWalletKey() {
        return aplWalletKey;
    }

    public void setAplWalletKey(AplWalletKey aplWalletKey) {
        this.aplWalletKey = aplWalletKey;
    }

    public EthWalletKey getEthWalletKey() {
        return ethWalletKey;
    }

    public EthWalletKey getPaxWalletKey() {
        return paxWalletKey;
    }

    public void setPaxWalletKey(EthWalletKey paxWalletKey) {
        this.paxWalletKey = paxWalletKey;
    }

    public void setEthWalletKey(EthWalletKey ethWalletKey) {
        this.ethWalletKey = ethWalletKey;
    }

    public String getEthAddress() {
        return ethWalletKey.getCredentials().getAddress();
    }

    public Long getAplId() {
        return aplWalletKey.getId();
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    @Deprecated
    public JSONObject toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("apl", getAplWalletKey().toJSON());
        jsonObject.put("eth", getEthWalletKey().toJSON());
        jsonObject.put("pax", getPaxWalletKey().toJSON());

         //For backward compatibility.
        jsonObject.put("account", getAplId());
        jsonObject.put("accountRS", Convert2.rsAccount(getAplId()));
        jsonObject.put("publicKey", Convert.toHexString(getAplWalletKey().getPublicKey()));
        jsonObject.put("ethAddress", getEthAddress());

        if (!StringUtils.isBlank(passphrase)) {
            jsonObject.put("passphrase", passphrase);
        }
        return jsonObject;
    }

    public JSONObject toJSON_v2() {
        JSONObject jsonObject = new JSONObject();
        List<JSONObject> currencies = new ArrayList<>();

        JSONObject ethObject = new JSONObject();
        List<JSONObject> ethWallets = new ArrayList<>();
        ethWallets.add(getEthWalletKey().toJSON());
        ethObject.put("currency", "eth");
        ethObject.put("wallets", ethWallets);

        JSONObject paxObject = new JSONObject();
        List<JSONObject> paxWallets = new ArrayList<>();
        paxWallets.add(getPaxWalletKey().toJSON());
        paxObject.put("currency", "pax");
        paxObject.put("wallets", paxWallets);

        //Apl info don't use right now.

//        JSONObject aplObject = new JSONObject();
//        List<JSONObject> aplWallets = new ArrayList<>();
//        aplWallets.add(getAplWalletKey().toJSON());
//        aplObject.put("currency", "apl");
//        aplObject.put("wallets", aplWallets);

        currencies.add(ethObject);
        currencies.add(paxObject);
//        currencies.add(aplObject);

        jsonObject.put("currencies", currencies);

        if (!StringUtils.isBlank(passphrase)) {
            jsonObject.put("passphrase", passphrase);
        }

        return jsonObject;
    }


}
