package io.nuls.poc.rpc.call;

import io.nuls.account.rpc.cmd.AccountResource;
import io.nuls.block.rpc.BlockResource;
import io.nuls.contract.vm.util.Constants;
import io.nuls.core.ModuleE;
import io.nuls.core.NulsDateUtils;
import io.nuls.core.RPCUtil;
import io.nuls.core.basic.AddressTool;
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
import io.nuls.poc.constant.ConsensusErrorCode;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.dto.CmdRegisterDto;
import io.nuls.poc.utils.compare.BlockHeaderComparator;
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

    /**
     * 账户验证
     * account validate
     *
     * @param chainId
     * @param address
     * @param password
     * @return validate result
     */
    public static Map accountValid(int chainId, String address, String password) {
        return accountResource.getPriKeyByAddress(CHAIN_ID, address, password);
    }

    /**
     * 查询多签账户信息
     * Query for multi-signature account information
     *
     * @param chainId
     * @param address
     * @return validate result
     */
    public static MultiSigAccount getMultiSignAccount(int chainId, String address) throws IOException, NulsException {
        String s = accountResource.getMultiSignAccount(CHAIN_ID, address);
        MultiSigAccount multiSigAccount = new MultiSigAccount();
        multiSigAccount.parse(RPCUtil.decode(s), 0);
        return multiSigAccount;
    }


    /**
     * 交易签名
     * transaction signature
     *
     * @param chainId
     * @param address
     * @param password
     * @param priKey
     * @param tx
     */
    public static void transactionSignature(int chainId, String address, String password, String priKey, Transaction tx) throws NulsException {
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
     * @param chain
     * @param address
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getBalanceAndNonce(Chain chain, String address, int assetChainId, int assetId) throws NulsException {
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
        try {

            long realTime = blockTime * 1000;
            Map<String, Object> params = new HashMap(4);
            params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
            long currentTime = NulsDateUtils.getCurrentTimeMillis();
            long surplusTime = realTime - currentTime;
            if (surplusTime <= MIN_PACK_SURPLUS_TIME) {
                return null;
            }
            params.put("endTimestamp", realTime - TIME_OUT);
            params.put("maxTxDataSize", chain.getConfig().getBlockMaxSize());
            params.put("blockTime", blockTime);
            params.put("packingAddress", packingAddress);
            BlockExtendsData preExtendsData = chain.getNewestHeader().getExtendsData();
            byte[] preStateRoot = preExtendsData.getStateRoot();
            params.put("preStateRoot", RPCUtil.encode(preStateRoot));
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_packableTxs", params, surplusTime - TIME_OUT);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Packaging transaction acquisition failure!");
                return null;
            }
            return (HashMap) ((HashMap) cmdResp.getResponseData()).get("tx_packableTxs");

        } catch (Exception e) {
            chain.getLogger().error(e);
            return null;
        }
    }

    /**
     * 获取指定交易
     * Acquisition of transactions based on transactions Hash
     *
     * @param chain  chain info
     * @param txHash transaction hash
     */
    @SuppressWarnings("unchecked")
    public static Transaction getTransaction(Chain chain, String txHash) {
        try {
            Map<String, Object> params = new HashMap(4);
            params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
            params.put("txHash", txHash);
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_getConfirmedTx", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Acquisition transaction failed！");
                return null;
            }
            Map responseData = (Map) cmdResp.getResponseData();
            Transaction tx = new Transaction();
            Map realData = (Map) responseData.get("tx_getConfirmedTx");
            String txHex = (String) realData.get("tx");
            if (!StringUtils.isBlank(txHex)) {
                tx.parse(RPCUtil.decode(txHex), 0);
            }
            return tx;
        } catch (Exception e) {
            chain.getLogger().error(e);
            return null;
        }
    }


    /**
     * 将新创建的交易发送给交易管理模块
     * The newly created transaction is sent to the transaction management module
     *
     * @param chain chain info
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static void sendTx(Chain chain, String tx) throws NulsException {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
        params.put("tx", tx);
        try {
            /*boolean ledgerValidResult = commitUnconfirmedTx(chain,tx);
            if(!ledgerValidResult){
                throw new NulsException(ConsensusErrorCode.FAILED);
            }*/
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_newTx", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Transaction failed to send!");
                //rollBackUnconfirmTx(chain,tx);
                throw new NulsException(ConsensusErrorCode.FAILED);
            }
        } catch (NulsException e) {
            throw e;
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
    }

    /**
     * 共识状态修改通知交易模块
     * Consensus status modification notification transaction module
     *
     * @param chain   chain info
     * @param packing packing state
     */
    @SuppressWarnings("unchecked")
    public static void sendState(Chain chain, boolean packing) {
        try {
            Map<String, Object> params = new HashMap(4);
            params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
            params.put("packaging", packing);
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_cs_state", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Packing state failed to send!");
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
    }

    /**
     * 批量验证交易
     *
     * @param chainId      链Id/chain id
     * @param transactions
     * @return
     */
    public static Response verify(int chainId, List<Transaction> transactions, BlockHeader header, BlockHeader lastHeader, NulsLogger logger) {
        try {
            Map<String, Object> params = new HashMap<>(2);
            params.put(Constants.CHAIN_ID, chainId);
            List<String> txList = new ArrayList<>();
            for (Transaction transaction : transactions) {
                txList.add(RPCUtil.encode(transaction.serialize()));
            }
            params.put("txList", txList);
            BlockExtendsData lastData = lastHeader.getExtendsData();
            params.put("preStateRoot", RPCUtil.encode(lastData.getStateRoot()));
            params.put("blockHeader", RPCUtil.encode(header.serialize()));
            return ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_batchVerify", params, 10 * 60 * 1000);
        } catch (Exception e) {
            logger.error("", e);
            return null;
        }
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
    public static List<byte[]> getEncryptedAddressList(Chain chain) {
        List<byte[]> packingAddressList = new ArrayList<>();
        try {
            Map<String, Object> params = new HashMap<>(2);
            params.put(ConsensusConstant.PARAM_CHAIN_ID, chain.getConfig().getChainId());
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.AC.abbr, "ac_getEncryptedAddressList", params);
            List<String> accountAddressList = (List<String>) ((HashMap) ((HashMap) cmdResp.getResponseData()).get("ac_getEncryptedAddressList")).get("list");
            if (accountAddressList != null && accountAddressList.size() > 0) {
                for (String address : accountAddressList) {
                    packingAddressList.add(AddressTool.getAddress(address));
                }
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
        return packingAddressList;
    }

    /**
     * 查询账户别名
     * Query account alias
     */
    public static String getAlias(Chain chain, String address) {
        String alias = null;
        try {
            Map<String, Object> params = new HashMap<>(2);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
            params.put("address", address);
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.AC.abbr, "ac_getAliasByAddress", params);
            HashMap result = (HashMap) ((HashMap) cmdResp.getResponseData()).get("ac_getAliasByAddress");
            String paramAlias = "alias";
            if (result.get(paramAlias) != null) {
                alias = (String) result.get("alias");
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
        return alias;
    }

    /**
     * 初始化链区块头数据，缓存指定数量的区块头
     * Initialize chain block header entity to cache a specified number of block headers
     *
     * @param chain chain info
     */
    @SuppressWarnings("unchecked")
    public static void loadBlockHeader(Chain chain) {
        List<BlockHeader> blockHeaders = blockResource.getLatestRoundBlockHeaders(chain.getConfig().getChainId(), ConsensusConstant.INIT_BLOCK_HEADER_COUNT)
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
     * @param chain chain info
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static boolean commitUnconfirmedTx(Chain chain, String tx) {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
        params.put("tx", tx);
        try {
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.LG.abbr, "commitUnconfirmedTx", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Ledger module verifies transaction failure!");
                return false;
            }
            HashMap result = (HashMap) ((HashMap) cmdResp.getResponseData()).get("commitUnconfirmedTx");
            int validateCode = (int) result.get("validateCode");
            if (validateCode == 1) {
                return true;
            } else {
                chain.getLogger().info("Ledger module verifies transaction failure,error info:" + result.get("validateDesc"));
                return false;
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
            return false;
        }
    }

    /**
     * 回滚交易在账本模块的记录
     * Rollback transactions recorded in the book module
     *
     * @param chain chain info
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static boolean rollBackUnconfirmTx(Chain chain, String tx) {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
        params.put("tx", tx);
        try {
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.LG.abbr, "rollBackUnconfirmTx", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Ledger module rollBack transaction failure!");
            }
            HashMap result = (HashMap) ((HashMap) cmdResp.getResponseData()).get("rollBackUnconfirmTx");
            int validateCode = (int) result.get("value");
            if (validateCode == 1) {
                return true;
            } else {
                chain.getLogger().info("Ledger module rollBack transaction failure!");
                return false;
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
            return false;
        }
    }

    /**
     * 交易基础验证
     * Transaction Basis Verification
     *
     * @param chain chain info
     * @param tx    transaction hex
     */
    @SuppressWarnings("unchecked")
    public static boolean transactionBasicValid(Chain chain, String tx) {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chain.getConfig().getChainId());
        params.put("tx", tx);
        try {
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_baseValidateTx", params);
            if (!cmdResp.isSuccess()) {
                chain.getLogger().error("Failure of transaction basic validation!");
                return false;
            }
            HashMap result = (HashMap) ((HashMap) cmdResp.getResponseData()).get("tx_baseValidateTx");
            return (boolean) result.get("value");
        } catch (Exception e) {
            chain.getLogger().error(e);
            return false;
        }
    }

    /**
     * 获取主网节点版本
     * Acquire account lock-in amount and available balance
     *
     * @param chainId
     */
    @SuppressWarnings("unchecked")
    public static Map getVersion(int chainId) throws NulsException {
        Map<String, Object> params = new HashMap(4);
        params.put(Constants.CHAIN_ID, chainId);
        try {
            Response callResp = ResponseMessageProcessor.requestAndResponse(ModuleE.PU.abbr, "getVersion", params);
            if (!callResp.isSuccess()) {
                return null;
            }
            return (Map) ((Map) callResp.getResponseData()).get("getVersion");
        } catch (Exception e) {
            throw new NulsException(e);
        }
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
