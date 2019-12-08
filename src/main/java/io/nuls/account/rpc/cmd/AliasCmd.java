package io.nuls.account.rpc.cmd;

import io.nuls.account.model.bo.Chain;
import io.nuls.account.service.AccountKeyStoreService;
import io.nuls.account.service.AccountService;
import io.nuls.account.service.AliasService;
import io.nuls.account.util.manager.ChainManager;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.Transaction;

import static io.nuls.account.util.LoggerUtil.LOG;

/**
 * @author: EdwardChan
 * @description: the Entry of Alias RPC
 * @date: Nov.20th 2018
 */
@Component
public class AliasCmd {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AliasService aliasService;
    @Autowired
    private AccountKeyStoreService keyStoreService;
    @Autowired
    private ChainManager chainManager;

    public String setAlias(int chainId, String address, String password, String alias) throws Exception {
        String txHash = null;
        Chain chain = chainManager.getChain(chainId);
        Transaction transaction = aliasService.setAlias(chain, address, password, alias);
        if (transaction != null && transaction.getHash() != null) {
            txHash = transaction.getHash().toHex();
        }
        return txHash;
    }

    public String getAliasByAddress(int chainId, String address) {
        return aliasService.getAliasByAddress(chainId, address);
    }

    /**
     * check whether the account is usable
     *
     * @return CmdResponse
     */
    public boolean isAliasUsable(int chainId, String alias) {
        return aliasService.isAliasUsable(chainId, alias);
    }

    private void errorLogProcess(Chain chain, Exception e) {
        if (chain == null) {
            LOG.error(e);
        } else {
            chain.getLogger().error(e);
        }
    }

}
