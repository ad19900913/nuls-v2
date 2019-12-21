/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.chain.rpc.cmd;


import io.nuls.chain.config.NulsChainConfig;
import io.nuls.chain.info.CmErrorCode;
import io.nuls.chain.info.CmRuntimeInfo;
import io.nuls.chain.model.dto.*;
import io.nuls.chain.model.po.Asset;
import io.nuls.chain.model.po.BlockChain;
import io.nuls.chain.model.po.CacheDatas;
import io.nuls.chain.model.po.ChainAsset;
import io.nuls.chain.model.tx.AddAssetToChainTransaction;
import io.nuls.chain.model.tx.DestroyAssetAndChainTransaction;
import io.nuls.chain.model.tx.RegisterChainAndAssetTransaction;
import io.nuls.chain.model.tx.RemoveAssetFromChainTransaction;
import io.nuls.chain.rpc.call.RpcService;
import io.nuls.chain.service.*;
import io.nuls.chain.util.LoggerUtil;
import io.nuls.core.NulsDateUtils;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.constant.BaseConstant;
import io.nuls.core.constant.ErrorCode;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.data.CoinData;
import io.nuls.core.data.Transaction;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.ByteUtils;
import io.nuls.core.model.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tangyi
 * @date 2018/11/7
 * @description
 */
@Component
public class ChainManagerResource extends BaseChainCmd {

    @Autowired
    NulsChainConfig nulsChainConfig;
    @Autowired
    private ChainService chainService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private RpcService rpcService;

    @Autowired
    private ValidateService validateService;
    @Autowired
    private CacheDataService cacheDataService;
    @Autowired
    private TxCirculateService txCirculateService;
    @Autowired
    private MessageService messageService;

    /**
     * 资产注册
     */
    public String assetReg(Asset asset, String address, String password) throws Exception {
        /* 发送到交易模块 (Send to transaction module) */
        if (asset.getDecimalPlaces() < Integer.parseInt(nulsChainConfig.getAssetDecimalPlacesMin()) || asset.getDecimalPlaces() > Integer.parseInt(nulsChainConfig.getAssetDecimalPlacesMax())) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_DECIMALPLACES);
        }
        if (null == asset.getSymbol() || asset.getSymbol().length() > Integer.parseInt(nulsChainConfig.getAssetSymbolMax()) || asset.getSymbol().length() < 1) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_SYMBOL_LENGTH);
        }
        asset.setDepositNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()));
        int rateToPercent = new BigDecimal(nulsChainConfig.getAssetDepositNulsDestroyRate()).multiply(BigDecimal.valueOf(100)).intValue();
        asset.setDestroyNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()).multiply(BigInteger.valueOf(rateToPercent)).divide(BigInteger.valueOf(100)));
        asset.setAvailable(true);
        BlockChain dbChain = chainService.getChain(asset.getChainId());
        if (null == dbChain) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_NOT_FOUND);
        }
        if (dbChain.isDelete()) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_REG_CMD);
        }
        if (assetService.assetExist(asset) && asset.isAvailable()) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_ID_EXIST);
        }
        /* 组装交易发送 (Send transaction) */
        Transaction tx = new AddAssetToChainTransaction();
        tx.setTxData(asset.parseToTransaction());
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());
        AccountBalance accountBalance = new AccountBalance(null, null);
        ErrorCode ldErrorCode = rpcService.getCoinData(address, accountBalance);
        if (null != ldErrorCode) {
            throw new NulsException(ldErrorCode);
        }
        CoinData coinData = this.getRegCoinData(asset, CmRuntimeInfo.getMainIntChainId(),
                CmRuntimeInfo.getMainIntAssetId(), tx.size(), accountBalance);
        tx.setCoinData(coinData.serialize());

        /* 判断签名是否正确 (Determine if the signature is correct) */
        ErrorCode acErrorCode = rpcService.transactionSignature(CmRuntimeInfo.getMainIntChainId(), address, password, tx);
        if (null != acErrorCode) {
            throw new NulsException(acErrorCode);
        }

        ErrorCode txErrorCode = rpcService.newTx(tx);
        if (null != txErrorCode) {
            throw new NulsException(txErrorCode);
        }
        return tx.getHash().toHex();
    }

    public String assetDisable(int chainId, int assetId, String addressStr, String password) throws Exception {
        /* 发送到交易模块 (Send to transaction module) */
        byte[] address = AddressTool.getAddress(addressStr);
        /* 身份的校验，账户地址的校验 (Verification of account address) */
        String dealAssetKey = CmRuntimeInfo.getAssetKey(chainId, assetId);
        Asset asset = assetService.getAsset(dealAssetKey);
        if (asset == null) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_NOT_EXIST);
        }
        if (!asset.isAvailable()) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_NOT_EXIST);
        }
        if (!ByteUtils.arrayEquals(asset.getAddress(), address)) {
            throw new NulsException(CmErrorCode.ERROR_ADDRESS_ERROR);
        }

        /*
          判断链下是否只有这一个资产了，如果是，则进行带链注销交易
          Judging whether there is only one asset under the chain, and if so, proceeding with the chain cancellation transaction
         */
        BlockChain dbChain = chainService.getChain(chainId);
        if (null == dbChain) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_NOT_EXIST);
        }

        List<String> assetKeyList = dbChain.getSelfAssetKeyList();
        int activeAssetCount = 0;
        String activeKey = "";
        for (String assetKey : assetKeyList) {
            Asset chainAsset = assetService.getAsset(assetKey);
            if (null != chainAsset && chainAsset.isAvailable()) {
                activeKey = assetKey;
                activeAssetCount++;
            }
            if (activeAssetCount > 1) {
                break;
            }
        }
        Transaction tx;
        if (activeAssetCount == 1 && activeKey.equalsIgnoreCase(dealAssetKey)) {
            /* 注销资产和链 (Destroy assets and chains) */
            tx = new DestroyAssetAndChainTransaction();
            tx.setTxData(dbChain.parseToTransaction(asset));
        } else {
            /* 只注销资产 (Only destroy assets) */
            tx = new RemoveAssetFromChainTransaction();
            tx.setTxData(asset.parseToTransaction());
        }
        AccountBalance accountBalance = new AccountBalance(null, null);
        ErrorCode ldErrorCode = rpcService.getCoinData(addressStr, accountBalance);
        if (null != ldErrorCode) {
            throw new NulsException(ldErrorCode);
        }
        CoinData coinData = this.getDisableCoinData(asset, CmRuntimeInfo.getMainIntChainId(), CmRuntimeInfo.getMainIntAssetId(), tx.size());
        tx.setCoinData(coinData.serialize());
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());

        /* 判断签名是否正确 (Determine if the signature is correct) */
        ErrorCode acErrorCode = rpcService.transactionSignature(CmRuntimeInfo.getMainIntChainId(), addressStr, password, tx);
        if (null != acErrorCode) {
            throw new NulsException(acErrorCode);
        }
        ErrorCode txErrorCode = rpcService.newTx(tx);
        if (null != txErrorCode) {
            throw new NulsException(txErrorCode);
        }
        return tx.getHash().toHex();
    }

    public Map<String, Object> getChainAsset(int chainId, int assetChainId, int assetId) throws Exception {
        String assetKey = CmRuntimeInfo.getAssetKey(assetChainId, assetId);
        ChainAsset chainAsset = assetService.getChainAsset(chainId, assetKey);
        Map<String, Object> rtMap = new HashMap<>();
        rtMap.put("chainId", chainId);
        rtMap.put("assetChainId", assetChainId);
        rtMap.put("assetId", assetId);
        rtMap.put("asset", chainAsset.getInitNumber().add(chainAsset.getInNumber()).subtract(chainAsset.getOutNumber()));
        return rtMap;
    }

    public RegAssetDto getRegAsset(int chainId, int assetId) throws Exception {
        String assetKey = CmRuntimeInfo.getAssetKey(chainId, assetId);
        Asset asset = assetService.getAsset(assetKey);
        if (null == asset) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_NOT_EXIST);
        } else {
            RegAssetDto regAssetDto = new RegAssetDto();
            regAssetDto.setChainId(chainId);
            regAssetDto.setAssetId(assetId);
            regAssetDto.setAddress(AddressTool.getStringAddressByBytes(asset.getAddress()));
            regAssetDto.setAssetName(asset.getAssetName());
            regAssetDto.setCreateTime(asset.getCreateTime());
            regAssetDto.setDecimalPlaces(asset.getDecimalPlaces());
            regAssetDto.setDepositNuls(asset.getDepositNuls().toString());
            regAssetDto.setDestroyNuls(asset.getDestroyNuls().toString());
            regAssetDto.setEnable(asset.isAvailable());
            regAssetDto.setInitNumber(asset.getInitNumber().toString());
            regAssetDto.setTxHash(asset.getTxHash());
            regAssetDto.setSymbol(asset.getSymbol());
            return regAssetDto;
        }
    }

    /**
     * 查询链上资产
     */
    public Map<String, Object> getCirculateChainAsset(int circulateChainId, int assetChainId, int assetId) throws Exception {
        ChainAsset chainAsset = txCirculateService.getCirculateChainAsset(circulateChainId, assetChainId, assetId);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("circulateChainId", circulateChainId);
        resultMap.put("assetChainId", assetChainId);
        resultMap.put("assetId", assetId);
        if (null != chainAsset) {
            resultMap.put("initNumber", chainAsset.getInitNumber());
            resultMap.put("chainAssetAmount", chainAsset.getInNumber().subtract(chainAsset.getOutNumber()));
            return resultMap;
        } else {
            throw new NulsException(CmErrorCode.ERROR_ASSET_NOT_EXIST);
        }
    }


    /**
     * 跨链流通校验
     */
    public void assetCirculateValidator(int chainId, Transaction tx) throws Exception {
        //提取 从哪条链 转 哪条链，是否是跨链，链 手续费共多少？
        if (null == tx) {
            throw new NulsException(CmErrorCode.ERROR_TX_HEX);
        }
        List<CoinDataAssets> list = txCirculateService.getChainAssetList(tx.getCoinData());
        CoinDataAssets fromCoinDataAssets = list.get(0);
        CoinDataAssets toCoinDataAssets = list.get(1);
        int fromChainId = fromCoinDataAssets.getChainId();
        int toChainId = toCoinDataAssets.getChainId();
        Map<String, BigInteger> fromAssetMap = fromCoinDataAssets.getAssetsMap();
        Map<String, BigInteger> toAssetMap = toCoinDataAssets.getAssetsMap();
        validateService.assetCirculateValidator(fromChainId, toChainId, fromAssetMap, toAssetMap);
    }

    /**
     * 跨链流通提交
     */
    public void assetCirculateCommit(int chainId, List<Transaction> txList, BlockHeader blockHeader) throws Exception {
        //A链转B链资产X，数量N ;A链X资产减少N, B链 X资产 增加N。
        long commitHeight = blockHeader.getHeight();
        /*begin bak datas*/
        cacheDataService.bakBlockTxs(chainId, commitHeight, txList, true);
        /*end bak datas*/
        /*begin bak height*/
        cacheDataService.beginBakBlockHeight(chainId, commitHeight);
        /*end bak height*/
        try {
            txCirculateService.circulateCommit(txList);
            /*begin bak height*/
            cacheDataService.endBakBlockHeight(chainId, commitHeight);
            /*end bak height*/
        } catch (Exception e) {
            LoggerUtil.logger().error(e);
            //进行回滚
            cacheDataService.rollBlockTxs(chainId, commitHeight);
        }
    }

    /**
     * 跨链流通回滚
     */
    public void assetCirculateRollBack(int chainId, BlockHeader blockHeader) throws Exception {
        long commitHeight = blockHeader.getHeight();
        //高度先回滚
        CacheDatas circulateTxDatas = cacheDataService.getCacheDatas(commitHeight - 1);
        if (null == circulateTxDatas) {
            LoggerUtil.logger().info("chain module height ={} bak datas is null,maybe had rolled", commitHeight);
        }
        //进行数据回滚
        cacheDataService.rollBlockTxs(chainId, commitHeight);
    }

    public void updateChainAsset(int chainId, List<Map> assets) {
        List<ChainAssetTotalCirculate> chainAssetTotalCirculates = new ArrayList<>();
        for (Map asset : assets) {
            ChainAssetTotalCirculate chainAssetTotalCirculate = new ChainAssetTotalCirculate();
            chainAssetTotalCirculate.setAssetId(Integer.parseInt(asset.get("assetId").toString()));
            chainAssetTotalCirculate.setChainId(chainId);
            chainAssetTotalCirculate.setAvailableAmount(new BigInteger(asset.get("availableAmount").toString()));
            chainAssetTotalCirculate.setFreeze(new BigInteger(asset.get("freeze").toString()));
            chainAssetTotalCirculates.add(chainAssetTotalCirculate);
        }
        messageService.recChainIssuingAssets(chainId, chainAssetTotalCirculates);
    }

    public RegChainDto chain(int chainId) throws Exception {
        BlockChain blockChain = chainService.getChain(chainId);
        RegChainDto regChainDto = new RegChainDto();
        regChainDto.buildRegChainDto(blockChain);
        regChainDto.setMainNetCrossConnectSeeds(rpcService.getCrossChainSeeds());
        regChainDto.setMainNetVerifierSeeds(rpcService.getChainPackerInfo(CmRuntimeInfo.getMainIntChainId()));
        return regChainDto;
    }

    public Map<String, Object> chainReg(Map params) throws Exception {
        /* 发送到交易模块 (Send to transaction module) */
        Map<String, Object> rtMap = new HashMap<>(1);
        /*判断链与资产是否已经存在*/
        /* 组装BlockChain (BlockChain object) */
        BlockChain blockChain = new BlockChain();
        blockChain.map2pojo(params);
        if (blockChain.getChainId() == BaseConstant.MAINNET_CHAIN_ID || blockChain.getChainId() == BaseConstant.TESTNET_CHAIN_ID) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_SYSTEM_USED);
        }
        String addressPrefix = (String) params.get("addressPrefix");
        if (StringUtils.isBlank(addressPrefix)) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
        }
        char[] arr = addressPrefix.toCharArray();
        if (arr.length > 5) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
        }
        for (char c : arr) {
            if (c < 48 || (c > 57 && c < 65) || (c > 90 && c < 97) || c > 122) {
                throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
            }
        }
        /* 组装Asset (Asset object) */
        /* 取消int assetId = seqService.createAssetId(blockChain.getChainId());*/
        Asset asset = new Asset();
        asset.map2pojo(params);
        if (asset.getDecimalPlaces() < Integer.parseInt(nulsChainConfig.getAssetDecimalPlacesMin()) || asset.getDecimalPlaces() > Integer.parseInt(nulsChainConfig.getAssetDecimalPlacesMax())) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_DECIMALPLACES);
        }
        if (null == asset.getSymbol() || asset.getSymbol().length() > Integer.parseInt(nulsChainConfig.getAssetSymbolMax()) || asset.getSymbol().length() < 1) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_SYMBOL_LENGTH);
        }
        asset.setChainId(blockChain.getChainId());
        asset.setDepositNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()));
        int rateToPercent = new BigDecimal(nulsChainConfig.getAssetDepositNulsDestroyRate()).multiply(BigDecimal.valueOf(100)).intValue();
        asset.setDestroyNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()).multiply(BigInteger.valueOf(rateToPercent)).divide(BigInteger.valueOf(100)));
        asset.setAvailable(true);
        BlockChain dbChain = chainService.getChain(blockChain.getChainId());
        if (null != dbChain && dbChain.isDelete()) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_REG_CMD);
        }
        if (null != dbChain) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ID_EXIST);
        }
        if (chainService.hadExistMagicNumber(blockChain.getMagicNumber())) {
            throw new NulsException(CmErrorCode.ERROR_MAGIC_NUMBER_EXIST);
        }
        if (chainService.hadExistChainName(blockChain.getChainName())) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_NAME_EXIST);
        }
        /* 组装交易发送 (Send transaction) */
        Transaction tx = new RegisterChainAndAssetTransaction();
        tx.setTxData(blockChain.parseToTransaction(asset));
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());
        AccountBalance accountBalance = new AccountBalance(null, null);
        ErrorCode ldErrorCode = rpcService.getCoinData(String.valueOf(params.get("address")), accountBalance);
        if (null != ldErrorCode) {
            throw new NulsException(ldErrorCode);
        }
        CoinData coinData = super.getRegCoinData(asset, CmRuntimeInfo.getMainIntChainId(),
                CmRuntimeInfo.getMainIntAssetId(), tx.size(),
                accountBalance);
        tx.setCoinData(coinData.serialize());

        /* 判断签名是否正确 (Determine if the signature is correct),取主网的chainid进行签名 */
        ErrorCode acErrorCode = rpcService.transactionSignature(CmRuntimeInfo.getMainIntChainId(), (String) params.get("address"), (String) params.get("password"), tx);
        if (null != acErrorCode) {
            throw new NulsException(acErrorCode);
        }

        rtMap.put("txHash", tx.getHash().toHex());
        rtMap.put("mainNetCrossConnectSeeds", rpcService.getCrossChainSeeds());
        rtMap.put("mainNetVerifierSeeds", rpcService.getChainPackerInfo(CmRuntimeInfo.getMainIntChainId()));


        ErrorCode txErrorCode = rpcService.newTx(tx);
        if (null != txErrorCode) {
            throw new NulsException(txErrorCode);
        }
        return rtMap;
    }


    public Map<String, Object> chainActive(Map params) throws Exception {
        /* 发送到交易模块 (Send to transaction module) */
        Map<String, Object> rtMap = new HashMap<>(1);
        /*判断链与资产是否已经存在*/
        /* 组装BlockChain (BlockChain object) */
        BlockChain blockChain = new BlockChain();
        blockChain.map2pojo(params);
        if (blockChain.getChainId() == BaseConstant.MAINNET_CHAIN_ID || blockChain.getChainId() == BaseConstant.TESTNET_CHAIN_ID) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_SYSTEM_USED);
        }
        String addressPrefix = (String) params.get("addressPrefix");
        if (StringUtils.isBlank(addressPrefix)) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
        }
        char[] arr = addressPrefix.toCharArray();
        if (arr.length > 5) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] < 48 || (arr[i] > 57 && arr[i] < 65) || (arr[i] > 90 && arr[i] < 97) || arr[i] > 122) {
                throw new NulsException(CmErrorCode.ERROR_CHAIN_ADDRESS_PREFIX);
            }
        }
        /* 组装Asset (Asset object) */
        /* 取消int assetId = seqService.createAssetId(blockChain.getChainId());*/
        Asset asset = new Asset();
        asset.map2pojo(params);
        if (asset.getDecimalPlaces() < Integer.valueOf(nulsChainConfig.getAssetDecimalPlacesMin()) || asset.getDecimalPlaces() > Integer.valueOf(nulsChainConfig.getAssetDecimalPlacesMax())) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_DECIMALPLACES);
        }
        if (null == asset.getSymbol() || asset.getSymbol().length() > Integer.valueOf(nulsChainConfig.getAssetSymbolMax()) || asset.getSymbol().length() < 1) {
            throw new NulsException(CmErrorCode.ERROR_ASSET_SYMBOL_LENGTH);
        }
        asset.setChainId(blockChain.getChainId());
        asset.setDepositNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()));
        int rateToPercent = new BigDecimal(nulsChainConfig.getAssetDepositNulsDestroyRate()).multiply(BigDecimal.valueOf(100)).intValue();
        asset.setDestroyNuls(new BigInteger(nulsChainConfig.getAssetDepositNuls()).multiply(BigInteger.valueOf(rateToPercent)).divide(BigInteger.valueOf(100)));
        asset.setAvailable(true);
        BlockChain dbChain = chainService.getChain(blockChain.getChainId());
        if (null == dbChain) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_NOT_FOUND);
        }
        if (null != dbChain && !dbChain.isDelete()) {
            throw new NulsException(CmErrorCode.ERROR_CHAIN_ACTIVE);
        }
        if (chainService.hadExistMagicNumber(blockChain.getMagicNumber()) && !dbChain.isDelete()) {
            throw new NulsException(CmErrorCode.ERROR_MAGIC_NUMBER_EXIST);
        }
        if (chainService.hadExistChainName(blockChain.getChainName()) && !dbChain.isDelete()) {
            LoggerUtil.COMMON_LOG.debug("######### delete={}", dbChain.isDelete());
            throw new NulsException(CmErrorCode.ERROR_CHAIN_NAME_EXIST);
        }
        /* 组装交易发送 (Send transaction) */
        Transaction tx = new RegisterChainAndAssetTransaction();
        tx.setTxData(blockChain.parseToTransaction(asset));
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());
        AccountBalance accountBalance = new AccountBalance(null, null);
        ErrorCode ldErrorCode = rpcService.getCoinData(String.valueOf(params.get("address")), accountBalance);
        if (null != ldErrorCode) {
            throw new NulsException(ldErrorCode);
        }
        CoinData coinData = super.getRegCoinData(asset, CmRuntimeInfo.getMainIntChainId(),
                CmRuntimeInfo.getMainIntAssetId(), tx.size(),
                accountBalance);
        tx.setCoinData(coinData.serialize());

        /* 判断签名是否正确 (Determine if the signature is correct),取主网的chainid进行签名 */
        ErrorCode acErrorCode = rpcService.transactionSignature(CmRuntimeInfo.getMainIntChainId(), (String) params.get("address"), (String) params.get("password"), tx);
        if (null != acErrorCode) {
            throw new NulsException(acErrorCode);
        }

        rtMap.put("txHash", tx.getHash().toHex());
        rtMap.put("mainNetCrossConnectSeeds", rpcService.getCrossChainSeeds());
        rtMap.put("mainNetVerifierSeeds", rpcService.getChainPackerInfo(CmRuntimeInfo.getMainIntChainId()));


        ErrorCode txErrorCode = rpcService.newTx(tx);
        if (null != txErrorCode) {
            throw new NulsException(txErrorCode);
        }
        return rtMap;
    }

    public List<Map<String, Object>> getCrossChainInfos(Map params) throws Exception {
        List<Map<String, Object>> chainInfos = new ArrayList<>();
        List<BlockChain> blockChains = chainService.getBlockList();
        for (BlockChain blockChain : blockChains) {
            chainInfos.add(chainService.getBlockAssetsInfo(blockChain));
        }
        return chainInfos;
    }

    public List<Map<String, Object>> getChainAssetsSimpleInfo() throws Exception {
        List<Map<String, Object>> chainInfos = new ArrayList<>();
        List<BlockChain> blockChains = chainService.getBlockList();
        for (BlockChain blockChain : blockChains) {
            chainInfos.add(chainService.getChainAssetsSimpleInfo(blockChain));
        }
        return chainInfos;
    }

}
