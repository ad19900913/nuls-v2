package io.nuls.poc.rpc.call;

import io.nuls.account.rpc.cmd.AccountResource;
import io.nuls.block.rpc.BlockResource;
import io.nuls.contract.vm.util.Constants;
import io.nuls.core.ModuleE;
import io.nuls.core.NulsDateUtils;
import io.nuls.core.RPCUtil;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.basic.ProtocolVersion;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.data.*;
import io.nuls.core.exception.NulsException;
import io.nuls.core.log.Log;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.core.model.StringUtils;
import io.nuls.core.signture.BlockSignature;
import io.nuls.core.signture.P2PHKSignature;
import io.nuls.core.signture.SignatureUtil;
import io.nuls.core.signture.TransactionSignature;
import io.nuls.ledger.rpc.cmd.LedgerResource;
import io.nuls.network.rpc.cmd.NetworkResource;
import io.nuls.poc.constant.ConsensusConstant;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.dto.CmdRegisterDto;
import io.nuls.poc.utils.compare.BlockHeaderComparator;
import io.nuls.protocol.rpc.ProtocolResource;
import io.nuls.transaction.rpc.cmd.TransactionResource;
import io.protostuff.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.Constant.CHAIN_ID;

/**
 * 公共远程方法调用工具类
 * Common Remote Method Call Tool Class
 *
 * @author tag
 * 2018/12/26
 */
public class CallMethodUtils {
    public static final long MIN_PACK_SURPLUS_TIME = 2000;
    public static final long TIME_OUT = 1000;

    @Autowired
    private static BlockResource blockResource;
    @Autowired
    private static AccountResource accountResource;
    @Autowired
    private static NetworkResource networkResource;
    @Autowired
    private static LedgerResource ledgerResource;
    @Autowired
    private static TransactionResource transactionResource;
    @Autowired
    private static ProtocolResource protocolResource;

    /**
     * 账户验证
     * account validate
     *
     * @param address
     * @param password
     * @return validate result
     */
    public static Map accountValid(String address, String password) {
        return accountResource.getPriKeyByAddress(CHAIN_ID, address, password);
    }

    /**
     * 查询多签账户信息
     * Query for multi-signature account information
     *
     * @param address
     * @return validate result
     */
    public static MultiSigAccount getMultiSignAccount(String address) throws IOException, NulsException {
        String s = accountResource.getMultiSignAccount(CHAIN_ID, address);
        MultiSigAccount multiSigAccount = new MultiSigAccount();
        multiSigAccount.parse(RPCUtil.decode(s), 0);
        return multiSigAccount;
    }


    /**
     * 交易签名
     * transaction signature
     *
     * @param address
     * @param password
     * @param priKey
     * @param tx
     */
    public static void transactionSignature(String address, String password, String priKey, Transaction tx) throws NulsException {
        try {
            P2PHKSignature p2PHKSignature = new P2PHKSignature();
            if (!StringUtils.isBlank(priKey)) {
                p2PHKSignature = SignatureUtil.createSignatureByPriKey(tx, priKey);
            } else {
                String s = accountResource.signDigest(CHAIN_ID, address, password, RPCUtil.encode(tx.getHash().getBytes()));
                p2PHKSignature.parse(RPCUtil.decode(s), 0);
            }
            TransactionSignature signature = new TransactionSignature();
            List<P2PHKSignature> p2PHKSignatures = new ArrayList<>();
            p2PHKSignatures.add(p2PHKSignature);
            signature.setP2PHKSignatures(p2PHKSignatures);
            tx.setTransactionSignature(signature.serialize());
        } catch (NulsException e) {
            throw e;
        } catch (Exception e) {
            throw new NulsException(e);
        }
    }

    /**
     * 区块签名
     * block signature
     *
     * @param chain
     * @param address
     * @param header
     */
    public static void blockSignature(Chain chain, String address, BlockHeader header) throws NulsException {
        try {
            String s = accountResource.signBlockDigest(CHAIN_ID, address, chain.getConfig().getPassword(), RPCUtil.encode(header.getHash().getBytes()));
            BlockSignature blockSignature = new BlockSignature();
            blockSignature.parse(RPCUtil.decode(s), 0);
            header.setBlockSignature(blockSignature);
        } catch (NulsException e) {
            throw e;
        } catch (Exception e) {
            throw new NulsException(e);
        }
    }

    /**
     * 将打包的新区块发送给区块管理模块
     *
     * @param chainId chain ID
     * @param block   new block Info
     * @return Successful Sending
     */
    @SuppressWarnings("unchecked")
    public static void receivePackingBlock(int chainId, Block block) {
        blockResource.receivePackingBlock(chainId, block);
    }

    /**
     * 获取网络节点连接数
     *
     * @param chainId chain ID
     * @param isCross 是否获取跨链节点连接数/Whether to Get the Number of Connections across Chains
     * @return int    连接节点数/Number of Connecting Nodes
     */
    public static int getAvailableNodeAmount(int chainId, boolean isCross) {
        return networkResource.getChainConnectAmount(CHAIN_ID, isCross);
    }

    /**
     * 获取可用余额和nonce
     * Get the available balance and nonce
     *
     * @param address
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getBalanceAndNonce(String address, int assetChainId, int assetId) {
        return ledgerResource.getBalanceNonce(CHAIN_ID, assetChainId, address, assetId, false);
    }

    /**
     * 获取账户锁定金额和可用余额
     * Acquire account lock-in amount and available balance
     *
     * @param chain
     * @param address
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getBalance(Chain chain, String address, int assetChainId, int assetId) throws NulsException {
        return ledgerResource.getBalance(CHAIN_ID, assetChainId, address, assetId);
    }

    /**
     * 获取打包交易
     * Getting Packaged Transactions
     *
     * @param chain chain info
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getPackingTxList(Chain chain, long blockTime, String packingAddress) {
        long realTime = blockTime * 1000;
        long currentTime = NulsDateUtils.getCurrentTimeMillis();
        long surplusTime = realTime - currentTime;
        if (surplusTime <= MIN_PACK_SURPLUS_TIME) {
            return null;
        }
        BlockExtendsData preExtendsData = chain.getNewestHeader().getExtendsData();
        byte[] preStateRoot = preExtendsData.getStateRoot();
        return transactionResource.packableTxs(CHAIN_ID, realTime - TIME_OUT, chain.getConfig().getBlockMaxSize(), blockTime, packingAddress, RPCUtil.encode(preStateRoot));
    }

    /**
     * 获取指定交易
     * Acquisition of transactions based on transactions Hash
     *
     * @param txHash transaction hash
     */
    @SuppressWarnings("unchecked")
    public static Transaction getTransaction(String txHash) throws IOException, NulsException {
        Transaction tx = new Transaction();
        String txHex = (String) transactionResource.getConfirmedTx(CHAIN_ID, txHash).get("tx");
        if (!StringUtils.isBlank(txHex)) {
            tx.parse(RPCUtil.decode(txHex), 0);
        }
        return tx;
    }


    /**
     * 将新创建的交易发送给交易管理模块
     * The newly created transaction is sent to the transaction management module
     *
     * @param tx transaction hex
     */
    @SuppressWarnings("unchecked")
    public static void sendTx(Transaction tx) throws NulsException {
        transactionResource.newTx(CHAIN_ID, tx);
    }

    /**
     * 共识状态修改通知交易模块
     * Consensus status modification notification transaction module
     *
     * @param packing packing state
     */
    @SuppressWarnings("unchecked")
    public static void sendState(boolean packing) {
        transactionResource.packaging(CHAIN_ID, packing);
    }

    /**
     * 批量验证交易
     *
     * @param transactions
     * @return
     */
    public static Map<String, Object> verify(List<Transaction> transactions, BlockHeader header, BlockHeader lastHeader, NulsLogger logger) throws Exception {
        List<String> txList = new ArrayList<>();
        for (Transaction transaction : transactions) {
            txList.add(RPCUtil.encode(transaction.serialize()));
        }
        BlockExtendsData lastData = lastHeader.getExtendsData();
        return transactionResource.batchVerify(CHAIN_ID, header, RPCUtil.encode(lastData.getStateRoot()), txList);
    }

    /**
     * 根据交易HASH获取NONCE（交易HASH后8位）
     * Obtain NONCE according to HASH (the last 8 digits of HASH)
     */
    public static String getNonce(String txHash) {
        return txHash.substring(txHash.length() - 8);
    }

    /**
     * 根据交易HASH获取NONCE（交易HASH后8位）
     * Obtain NONCE according to HASH (the last 8 digits of HASH)
     */
    public static byte[] getNonce(byte[] txHash) {
        byte[] targetArr = new byte[8];
        System.arraycopy(txHash, txHash.length - 8, targetArr, 0, 8);
        return targetArr;
    }

    /**
     * 查询本地加密账户
     * Search for Locally Encrypted Accounts
     */
    @SuppressWarnings("unchecked")
    public static List<byte[]> getEncryptedAddressList() {
        List<byte[]> packingAddressList = new ArrayList<>();
        List<String> accountAddressList = accountResource.getEncryptedAddressList(CHAIN_ID);
        if (accountAddressList != null && accountAddressList.size() > 0) {
            for (String address : accountAddressList) {
                packingAddressList.add(AddressTool.getAddress(address));
            }
        }
        return packingAddressList;
    }

    /**
     * 查询账户别名
     * Query account alias
     */
    public static String getAlias(String address) {
        return accountResource.getAliasByAddress(CHAIN_ID, address);
    }

    /**
     * 初始化链区块头数据，缓存指定数量的区块头
     * Initialize chain block header entity to cache a specified number of block headers
     *
     * @param chain chain info
     */
    @SuppressWarnings("unchecked")
    public static void loadBlockHeader(Chain chain) {
        List<BlockHeader> blockHeaders = blockResource.getLatestRoundBlockHeaders(chain.getConfig().getChainId(), ConsensusConstant.INIT_BLOCK_HEADER_COUNT);
        blockHeaders.sort(new BlockHeaderComparator());
        chain.setBlockHeaderList(blockHeaders);
        chain.setNewestHeader(blockHeaders.get(blockHeaders.size() - 1));
        Log.debug("---------------------------区块加载成功！");
    }


    /**
     * 初始化链区块头数据，缓存指定数量的区块头
     * Initialize chain block header entity to cache a specified number of block headers
     *
     * @param chain chain info
     */
    @SuppressWarnings("unchecked")
    public static void getRoundBlockHeaders(Chain chain, int roundCount, long startHeight) {
        int chainId = chain.getConfig().getChainId();
        List<BlockHeader> blockHeaders = blockResource.getRoundBlockHeaders(chainId, startHeight, roundCount);
        blockHeaders.sort(new BlockHeaderComparator());
        chain.getBlockHeaderList().addAll(0, blockHeaders);
        Log.debug("---------------------------回滚区块轮次变化从新加载区块成功！");
    }


    /**
     * 验证交易CoinData
     * Verifying transactions CoinData
     *
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static boolean commitUnconfirmedTx(Transaction tx) throws Exception {
        return ledgerResource.commitUnconfirmedTx(CHAIN_ID, tx);
    }

    /**
     * 回滚交易在账本模块的记录
     * Rollback transactions recorded in the book module
     *
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static boolean rollBackUnconfirmTx(Transaction tx) {
        return ledgerResource.rollBackUnconfirmTx(CHAIN_ID, tx);
    }

    /**
     * 获取主网节点版本
     * Acquire account lock-in amount and available balance
     *
     */
    @SuppressWarnings("unchecked")
    public static Map<String, ProtocolVersion> getVersion() {
        return protocolResource.getVersion(CHAIN_ID);
    }

    /**
     * 注册智能合约交易
     * Acquire account lock-in amount and available balance
     *
     * @param chainId
     */
    @SuppressWarnings("unchecked")
    public static boolean registerContractTx(int chainId, List<CmdRegisterDto> cmdRegisterDtoList) {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chainId);
        params.put("moduleCode", ModuleE.CS.abbr);
        params.put("cmdRegisterList", cmdRegisterDtoList);
        try {
            Response callResp = ResponseMessageProcessor.requestAndResponse(ModuleE.SC.abbr, "sc_register_cmd_for_contract", params);
            return callResp.isSuccess();
        } catch (Exception e) {
            Log.error(e);
            return false;
        }
    }

    /**
     * 触发CoinBase智能合约
     * Acquire account lock-in amount and available balance
     *
     * @param chainId
     */
    @SuppressWarnings("unchecked")
    public static String triggerContract(int chainId, String stateRoot, long height, String contractAddress, String coinBaseTx) {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chainId);
        params.put("stateRoot", stateRoot);
        params.put("blockHeight", height);
        params.put("contractAddress", contractAddress);
        params.put("tx", coinBaseTx);
        try {
            Response callResp = ResponseMessageProcessor.requestAndResponse(ModuleE.SC.abbr, "sc_trigger_payable_for_consensus_contract", params);
            if (!callResp.isSuccess()) {
                return null;
            }
            HashMap result = (HashMap) ((HashMap) callResp.getResponseData()).get("sc_trigger_payable_for_consensus_contract");
            return (String) result.get("value");
        } catch (Exception e) {
            Log.error(e);
            return null;
        }
    }
}
