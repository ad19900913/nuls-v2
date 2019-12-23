package io.nuls.transaction.rpc.cmd;

import io.nuls.core.RPCUtil;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.data.NulsHash;
import io.nuls.core.data.Transaction;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.rpc.model.*;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.protocol.TxRegisterDetail;
import io.nuls.transaction.cache.PackablePool;
import io.nuls.transaction.constant.TxCmd;
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
import java.util.ArrayList;
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

    public Map<String, Object> packableTxs(int chainId, long endTimestamp, int maxTxDataSize, long blockTime, String packingAddress, String preStateRoot) {
        Chain chain = null;
        chain = chainManager.getChain(chainId);
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
     * @param params Map
     * @return Response
     */
    @CmdAnnotation(cmd = TxCmd.TX_SAVE, priority = CmdPriority.HIGH, version = 1.0, description = "保存新区块的交易/Save the confirmed transaction")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "待保存的交易集合"),
            @Parameter(parameterName = "contractList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "智能合约交易"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "区块头")
    })
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "是否成功")
    }))
    public boolean txSave(int chainId) {
        Map<String, Boolean> map = new HashMap<>(TxConstant.INIT_CAPACITY_16);
        boolean result = false;
        Chain chain = chainManager.getChain(chainId);
        List<String> txStrList = (List<String>) params.get("txList");
        if (null == txStrList) {
            throw new NulsException(TxErrorCode.PARAMETER_ERROR);
        }
        List<String> contractList = (List<String>) params.get("contractList");
        result = confirmedTxService.saveTxList(chain, txStrList, contractList, (String) params.get("blockHeader"));
        return result;
    }

    @CmdAnnotation(cmd = TxCmd.TX_GENGSIS_SAVE, version = 1.0, description = "保存创世块的交易/Save the transactions of the Genesis block ")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "待保存的交易集合"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "区块头")
    })
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "是否成功")
    }))
    public boolean txGengsisSave(Map params) throws NulsException {
        Map<String, Boolean> map = new HashMap<>(TxConstant.INIT_CAPACITY_16);
        boolean result = false;
        Chain chain = chainManager.getChain((Integer) params.get("chainId"));
        List<String> txStrList = (List<String>) params.get("txList");
        result = confirmedTxService.saveGengsisTxList(chain, txStrList, (String) params.get("blockHeader"));
        return result;
    }

    @CmdAnnotation(cmd = TxCmd.TX_ROLLBACK, priority = CmdPriority.HIGH, version = 1.0, description = "回滚区块的交易/transaction rollback")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id"),
            @Parameter(parameterName = "txHashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "待回滚交易集合"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "区块头")
    })
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "是否成功")
    }))
    public boolean txRollback(int chainId) throws NulsException {
        boolean result;
        Chain chain = chainManager.getChain((Integer) params.get("chainId"));
        List<String> txHashStrList = (List<String>) params.get("txHashList");
        List<NulsHash> txHashList = new ArrayList<>();
        //将交易hashHex解码为交易hash字节数组
        for (String hashStr : txHashStrList) {
            txHashList.add(NulsHash.fromHex(hashStr));
        }
        //批量回滚已确认交易
        result = confirmedTxService.rollbackTxList(chain, txHashList, (String) params.get("blockHeader"));
        return result;
    }

    public List<Integer> getSystemTypes(int chainId) {
        Chain chain = chainManager.getChain(chainId);
        return TxManager.getSysTypes(chain);
    }

    public String getTx(int chainId, String txHash) throws NulsException, IOException {
        Chain chain = chainManager.getChain(chainId);
        if (!NulsHash.validHash(txHash)) {
            throw new NulsException(TxErrorCode.HASH_ERROR);
        }
        TransactionConfirmedPO tx = txService.getTransaction(chain, NulsHash.fromHex(txHash));
        return RPCUtil.encode(tx.getTx().serialize());
    }

    public String getConfirmedTx(int chainId, String txHash) throws IOException, NulsException {
        Chain chain = chainManager.getChain(chainId);
        if (!NulsHash.validHash(txHash)) {
            throw new NulsException(TxErrorCode.HASH_ERROR);
        }
        TransactionConfirmedPO tx = confirmedTxService.getConfirmedTransaction(chain, NulsHash.fromHex(txHash));
        return RPCUtil.encode(tx.getTx().serialize());
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