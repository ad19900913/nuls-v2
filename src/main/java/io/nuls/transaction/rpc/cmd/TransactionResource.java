package io.nuls.transaction.rpc.cmd;

import io.nuls.core.RPCUtil;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.data.NulsHash;
import io.nuls.core.data.Transaction;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.protocol.TxRegisterDetail;
import io.nuls.transaction.cache.PackablePool;
import io.nuls.transaction.constant.TxConstant;
import io.nuls.transaction.constant.TxErrorCode;
import io.nuls.transaction.manager.ChainManager;
import io.nuls.transaction.manager.TxManager;
import io.nuls.transaction.model.bo.Chain;
import io.nuls.transaction.model.bo.TxPackage;
import io.nuls.transaction.model.bo.VerifyLedgerResult;
import io.nuls.transaction.model.bo.VerifyResult;
import io.nuls.transaction.model.dto.ModuleTxRegisterDTO;
import io.nuls.transaction.model.po.TransactionConfirmedPO;
import io.nuls.transaction.rpc.call.LedgerCall;
import io.nuls.transaction.service.ConfirmedTxService;
import io.nuls.transaction.service.TxService;
import io.nuls.transaction.utils.TxUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.transaction.utils.LoggerUtil.LOG;

/**
 * @author: Charlie
 * @date: 2018/11/12
 */
@Component
public class TransactionResource {

    @Autowired
    private TxService txService;
    @Autowired
    private ConfirmedTxService confirmedTxService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private PackablePool packablePool;

    public Map<String, Object> getTx(int chainId, String txHash) throws NulsException, IOException {
        Chain chain = chainManager.getChain(chainId);
        if (!NulsHash.validHash(txHash)) {
            throw new NulsException(TxErrorCode.HASH_ERROR);
        }
        TransactionConfirmedPO tx = txService.getTransaction(chain, NulsHash.fromHex(txHash));
        Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_4);
        if (tx == null) {
            resultMap.put("tx", null);
        } else {
            resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
            resultMap.put("height", tx.getBlockHeight());
            resultMap.put("status", tx.getStatus());
        }
        return resultMap;
    }

    public Map<String, Object> getConfirmedTx(int chainId, String txHash) throws NulsException, IOException {
        Chain chain = chainManager.getChain(chainId);
        if (!NulsHash.validHash(txHash)) {
            throw new NulsException(TxErrorCode.HASH_ERROR);
        }
        TransactionConfirmedPO tx = confirmedTxService.getConfirmedTransaction(chain, NulsHash.fromHex(txHash));
        Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_4);
        if (tx == null) {
            LOG.debug("getConfirmedTransaction fail, tx is null. txHash:{}", txHash);
            resultMap.put("tx", null);
        } else {
            LOG.debug("getConfirmedTransaction success. txHash:{}", txHash);
            resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
            resultMap.put("height", tx.getBlockHeight());
            resultMap.put("status", tx.getStatus());
        }
        return resultMap;
    }


    public String verifyTx(int chainId, Transaction tx) throws IOException {
        Chain chain = chainManager.getChain(chainId);
        VerifyResult verifyResult = txService.verify(chain, tx, true);
        if (!verifyResult.getResult()) {
            throw new NulsRuntimeException(verifyResult.getErrorCode());
        }
        VerifyLedgerResult verifyLedgerResult = LedgerCall.verifyCoinData(chain, RPCUtil.encode(tx.serialize()));
        if (!verifyLedgerResult.getSuccess()) {
            throw new NulsRuntimeException(verifyLedgerResult.getErrorCode());
        }
        return tx.getHash().toHex();
    }

    public boolean register(int chainId, ModuleTxRegisterDTO moduleTxRegisterDto) throws NulsException {
        Chain chain = chainManager.getChain(moduleTxRegisterDto.getChainId());
        List<TxRegisterDetail> txRegisterList = moduleTxRegisterDto.getList();
        if (moduleTxRegisterDto == null || txRegisterList == null) {
            throw new NulsException(TxErrorCode.TX_NOT_EXIST);
        }
        return txService.register(chain, moduleTxRegisterDto);
    }

    public Map<String, Object> newTx(int chainId, Transaction transaction) throws NulsException {
        Chain chain = chainManager.getChain(chainId);
        //将交易放入待验证本地交易队列中
        txService.newTx(chain, transaction);
        Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_4);
        map.put("value", true);
        map.put("hash", transaction.getHash().toHex());
        return map;
    }

    public Map<String, Object> packableTxs(int chainId, long endTimestamp, long maxTxDataSize, long blockTime, String packingAddress, String preStateRoot) {
        Chain chain = chainManager.getChain(chainId);
        TxPackage txPackage = txService.getPackableTxs(chain, endTimestamp, maxTxDataSize, blockTime, packingAddress, preStateRoot);
        Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_4);
        map.put("list", txPackage.getList());
        map.put("stateRoot", txPackage.getStateRoot());
        map.put("packageHeight", txPackage.getPackageHeight());
        return map;
    }

    public void backPackableTxs(int chainId, List<Transaction> transactions) {
        Chain chain = chainManager.getChain(chainId);
        for (Transaction transaction : transactions) {
            packablePool.offerFirstOnlyHash(chain, transaction);
        }
    }

    /**
     * Save the transaction in the new block that was verified to the database
     * 保存新区块的交易
     *
     * @return Response
     */
    public boolean txSave(int chainId, List<String> txStrList, List<String> contractList, BlockHeader blockHeader) throws NulsException {
        Chain chain = chainManager.getChain(chainId);
        return confirmedTxService.saveTxList(chain, txStrList, contractList, blockHeader);
    }

    public boolean txGengsisSave(int chainId, List<String> txStrList, BlockHeader blockHeader) throws NulsException {
        Chain chain = chainManager.getChain(chainId);
        return confirmedTxService.saveGengsisTxList(chain, txStrList, blockHeader);
    }

    public boolean txRollback(int chainId, List<NulsHash> txHashList, BlockHeader blockHeader) throws NulsException {
        Chain chain = chainManager.getChain(chainId);
        //批量回滚已确认交易
        return confirmedTxService.rollbackTxList(chain, txHashList, blockHeader);
    }

    public List<Integer> getSystemTypes(int chainId) {
        Chain chain = chainManager.getChain(chainId);
        return TxManager.getSysTypes(chain);
    }

    public List<String> getBlockTxs(int chainId, List<String> txHashList) {
        Chain chain = chainManager.getChain(chainId);
        return confirmedTxService.getTxList(chain, txHashList);
    }

    public List<String> getBlockTxsExtend(int chainId, List<String> txHashList, boolean allHits) {
        Chain chain = chainManager.getChain(chainId);
        return confirmedTxService.getTxListExtend(chain, txHashList, allHits);
    }


    public List<String> getNonexistentUnconfirmedHashs(int chainId, List<String> txHashList) {
        Chain chain = chainManager.getChain(chainId);
        return confirmedTxService.getNonexistentUnconfirmedHashList(chain, txHashList);
    }


    public Map<String, Object> batchVerify(int chainId, BlockHeader blockHeader, String preStateRoot, List<String> txList) throws Exception {
        Chain chain = chainManager.getChain(chainId);
        return txService.batchVerify(chain, txList, blockHeader, preStateRoot);
    }

    public void packaging(int chainId, boolean packaging) {
        Chain chain = chainManager.getChain(chainId);
        chain.getPackaging().set(packaging);
        chain.getLogger().debug("Task-Packaging 节点是否是打包节点,状态变更为: {}", chain.getPackaging().get());
    }


    public void blockNotice(int chainId, int status) {
        Chain chain = chainManager.getChain(chainId);
        if (1 == status) {
            chain.getProcessTxStatus().set(true);
            chain.getLogger().info("节点区块同步状态变更为: true");
        } else {
            chain.getProcessTxStatus().set(false);
            chain.getLogger().info("节点区块同步状态变更为: false");
        }
    }

    /**
     * 最新区块高度
     *
     * @return
     */
    public void height(int chainId,long height) {
        Chain chain = chainManager.getChain(chainId);
        chain.setBestBlockHeight(height);
        chain.getLogger().debug("最新已确认区块高度更新为: [{}]" + TxUtil.nextLine() + TxUtil.nextLine(), height);
    }

    private void errorLogProcess(Chain chain, Exception e) {
        if (chain == null) {
            LOG.error(e);
        } else {
            chain.getLogger().error(e);
        }
    }

}