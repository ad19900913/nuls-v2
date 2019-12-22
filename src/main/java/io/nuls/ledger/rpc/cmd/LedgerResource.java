/*-
 * ⁣⁣
 * MIT License
 * ⁣⁣
 * Copyright (C) 2017 - 2018 nuls.io
 * ⁣⁣
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ⁣⁣
 */
package io.nuls.ledger.rpc.cmd;

import io.nuls.core.RPCUtil;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.Transaction;
import io.nuls.ledger.constant.LedgerConstant;
import io.nuls.ledger.model.FreezeLockState;
import io.nuls.ledger.model.ValidateResult;
import io.nuls.ledger.model.po.AccountState;
import io.nuls.ledger.model.po.AccountStateUnconfirmed;
import io.nuls.ledger.model.po.sub.FreezeHeightState;
import io.nuls.ledger.model.po.sub.FreezeLockTimeState;
import io.nuls.ledger.service.AccountStateService;
import io.nuls.ledger.service.ChainAssetsService;
import io.nuls.ledger.service.TransactionService;
import io.nuls.ledger.service.UnconfirmedStateService;
import io.nuls.ledger.utils.LoggerUtil;
import io.nuls.ledger.validator.CoinDataValidator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于获取账户余额及账户nonce值
 * Created by wangkun23 on 2018/11/19
 *
 * @author lanjinsheng .
 */
@Component
public class LedgerResource {


    @Autowired
    private AccountStateService accountStateService;
    @Autowired
    private UnconfirmedStateService unconfirmedStateService;
    @Autowired
    ChainAssetsService chainAssetsService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    CoinDataValidator coinDataValidator;

    /**
     * validate coin entity
     * 进行nonce-hash校验，进行可用余额校验
     *
     * @return
     */

    public Map<String, Object> verifyCoinDataBatchPackaged(int chainId, List<Transaction> txList) {
        List<String> orphanList = new ArrayList<>();
        List<String> successList = new ArrayList<>();
        List<String> failList = new ArrayList<>();
        for (Transaction tx : txList) {
            String txHash = tx.getHash().toHex();
            ValidateResult validateResult = coinDataValidator.bathValidatePerTx(chainId, tx);
            if (validateResult.isSuccess()) {
                //success
                successList.add(txHash);
            } else if (validateResult.isOrphan()) {
                orphanList.add(txHash);
            } else {
                failList.add(txHash);
            }
        }
        Map<String, Object> rtMap = new HashMap<>(3);
        if (failList.size() > 0) {
            LoggerUtil.logger(chainId).debug("verifyCoinDataBatchPackaged failed txs size={}", failList.size());
        }
        rtMap.put("fail", failList);
        rtMap.put("orphan", orphanList);
        rtMap.put("success", successList);
        return rtMap;
    }

    /**
     * validate coin entity
     * 进行nonce-hash校验，进行单笔交易的未确认校验
     * 用于第三方打包交易校验
     *
     * @return
     */
    public boolean verifyCoinData(int chainId, Transaction tx) throws Exception {
        ValidateResult validateResult = coinDataValidator.verifyCoinData(chainId, tx);
        return validateResult.isOrphan();
    }

    /**
     * 回滚打包确认交易状态
     *
     * @return
     */
    public boolean rollbackTxValidateStatus(int chainId, Transaction tx) {
        LoggerUtil.logger(chainId).debug("rollbackrTxValidateStatus chainId={},txHash={}", chainId, tx.getHash().toHex());
        //未确认回滚已被调用方处理过了
        return coinDataValidator.rollbackTxValidateStatus(chainId, tx);

    }

    /**
     * bathValidateBegin
     *
     * @return
     */
    public boolean batchValidateBegin(int chainId) {
        LoggerUtil.logger(chainId).debug("chainId={} batchValidateBegin", chainId);
        return coinDataValidator.beginBatchPerTxValidate(chainId);
    }

    /**
     * 接收到peer区块时调用验证
     *
     * @return
     */
    public boolean blockValidate(int chainId, long blockHeight, List<Transaction> txList) {
        LoggerUtil.logger(chainId).debug("chainId={} blockHeight={} blockValidate", chainId, blockHeight);
        LoggerUtil.logger(chainId).debug("commitBlockTxs txHexListSize={}", txList.size());
        boolean b = coinDataValidator.blockValidate(chainId, blockHeight, txList);
        LoggerUtil.logger(chainId).debug("chainId={} blockHeight={},return={}", chainId, blockHeight, b);
        return b;
    }

    /**
     * 未确认交易提交
     *
     * @return
     */

    public boolean commitUnconfirmedTx(int chainId, Transaction tx) throws Exception {
        ValidateResult validateResult = transactionService.unConfirmTxProcess(chainId, tx);
        return validateResult.isOrphan();
    }

    /**
     * 未确认交易提交
     *
     * @return
     */
    public Map<String, Object> commitBatchUnconfirmedTxs(int chainId, List<Transaction> txList) throws Exception {
        List<String> orphanList = new ArrayList<>();
        List<String> failList = new ArrayList<>();
        for (Transaction tx : txList) {
            String txHash = tx.getHash().toHex();
            ValidateResult validateResult = transactionService.unConfirmTxProcess(chainId, tx);
            if (validateResult.isSuccess()) {
                //success
            } else if (validateResult.isOrphan()) {
                orphanList.add(txHash);
            } else {
                failList.add(txHash);
            }
        }
        Map<String, Object> rtMap = new HashMap<>(2);
        rtMap.put("fail", failList);
        rtMap.put("orphan", orphanList);
        return rtMap;

    }

    /**
     * 区块交易提交
     *
     * @return
     */
    public boolean commitBlockTxs(int chainId, long blockHeight, List<Transaction> txList) {
        LoggerUtil.logger(chainId).info("commitBlockTxs chainId={},blockHeight={},txs={}", chainId, blockHeight, txList.size());
        return transactionService.confirmBlockProcess(chainId, txList, blockHeight);
    }

    /**
     * 逐笔回滚未确认交易
     *
     * @return
     */
    public boolean rollBackUnconfirmTx(int chainId, Transaction tx) {
        LoggerUtil.logger(chainId).debug("rollBackUnconfirmTx chainId={},txHash={}", chainId, tx.getHash().toHex());
        return transactionService.rollBackUnconfirmTx(chainId, tx);

    }

    public void clearUnconfirmTxs(int chainId) throws Exception {
        LoggerUtil.logger(chainId).debug("clearUnconfirmTxs chainId={}", chainId);
        unconfirmedStateService.clearAllAccountUnconfirmed(chainId);
    }

    /**
     * 回滚区块交易
     *
     * @return
     */
    public boolean rollBackBlockTxs(int chainId, long blockHeight, List<Transaction> txList) {
        LoggerUtil.logger(chainId).debug("rollBackBlockTxs chainId={},blockHeight={},txStrList={}", chainId, blockHeight, txList.size());
        boolean value = transactionService.rollBackConfirmTxs(chainId, blockHeight, txList);
        LoggerUtil.logger(chainId).debug("rollBackBlockTxs response={}", value);
        return value;
    }

    public List<Map<String, Object>> getAssetsById(int chainId, String assetIds) {
        List<Map<String, Object>> rtAssetList = new ArrayList<>();
        String[] assetIdList = assetIds.split(LedgerConstant.COMMA);
        for (String assetIdStr : assetIdList) {
            Map<String, Object> map = chainAssetsService.getAssetByChainAssetId(chainId, chainId, Integer.parseInt(assetIdStr));
            rtAssetList.add(map);
        }
        return rtAssetList;
    }

    /**
     * 获取账户资产余额
     * get user account balance
     *
     * @return
     */
    public Map<String, Object> getBalance(int chainId, int assetChainId, String address, int assetId) {
        LoggerUtil.logger(chainId).debug("chainId={},assetChainId={},address={},assetId={}", chainId, assetChainId, address, assetId);
        AccountState accountState = accountStateService.getAccountStateReCal(address, chainId, assetChainId, assetId);
        Map<String, Object> rtMap = new HashMap<>(5);
        rtMap.put("freeze", accountState.getFreezeTotal());
        rtMap.put("total", accountState.getTotalAmount());
        rtMap.put("available", accountState.getAvailableAmount());
        BigInteger permanentLocked = BigInteger.ZERO;
        BigInteger timeHeightLocked = BigInteger.ZERO;
        for (FreezeLockTimeState freezeLockTimeState : accountState.getFreezeLockTimeStates()) {
            if (LedgerConstant.PERMANENT_LOCK == freezeLockTimeState.getLockTime()) {
                permanentLocked = permanentLocked.add(freezeLockTimeState.getAmount());
            } else {
                timeHeightLocked = timeHeightLocked.add(freezeLockTimeState.getAmount());
            }
        }
        for (FreezeHeightState freezeHeightState : accountState.getFreezeHeightStates()) {
            timeHeightLocked = timeHeightLocked.add(freezeHeightState.getAmount());
        }
        rtMap.put("permanentLocked", permanentLocked);
        rtMap.put("timeHeightLocked", timeHeightLocked);
        return rtMap;
    }

    /**
     * 获取账户锁定列表
     * get user account freeze
     *
     * @return
     */
    public Map<String, Object> getFreezeList(int chainId, int assetChainId, String address, int assetId, int pageNumber, int pageSize) {
        AccountState accountState = accountStateService.getAccountStateReCal(address, chainId, assetChainId, assetId);
        List<FreezeLockState> freezeLockStates = new ArrayList<>();

        for (FreezeLockTimeState freezeLockTimeState : accountState.getFreezeLockTimeStates()) {
            FreezeLockState freezeLockState = new FreezeLockState();
            freezeLockState.setAmount(freezeLockTimeState.getAmount());
            freezeLockState.setLockedValue(freezeLockTimeState.getLockTime());
            freezeLockState.setTime(freezeLockTimeState.getCreateTime());
            freezeLockState.setTxHash(freezeLockTimeState.getTxHash());
            freezeLockStates.add(freezeLockState);
        }
        for (FreezeHeightState freezeHeightState : accountState.getFreezeHeightStates()) {
            FreezeLockState freezeLockState = new FreezeLockState();
            freezeLockState.setAmount(freezeHeightState.getAmount());
            freezeLockState.setLockedValue(freezeHeightState.getHeight());
            freezeLockState.setTime(freezeHeightState.getCreateTime());
            freezeLockState.setTxHash(freezeHeightState.getTxHash());
            freezeLockStates.add(freezeLockState);
        }
        freezeLockStates.sort((x, y) -> Long.compare(y.getTime(), x.getTime()));
        //get by page
        int currIdx = (pageNumber > 1 ? (pageNumber - 1) * pageSize : 0);
        List<FreezeLockState> resultList = new ArrayList<>();
        if ((currIdx + pageSize) > freezeLockStates.size()) {
            resultList = freezeLockStates.subList(currIdx, freezeLockStates.size());
        } else {
            resultList = freezeLockStates.subList(currIdx, currIdx + pageSize);
        }
        Map<String, Object> rtMap = new HashMap<>(4);
        rtMap.put("totalCount", freezeLockStates.size());
        rtMap.put("pageNumber", pageNumber);
        rtMap.put("pageSize", pageSize);
        rtMap.put("list", resultList);
        return rtMap;
    }

    /**
     * 获取账户nonce值
     * get user account nonce
     *
     * @return
     */
    public Map<String, Object> getNonce(int chainId, int assetChainId, String address, int assetId, boolean isConfirmed) {
        Map<String, Object> rtMap = new HashMap<>(2);
        AccountState accountState = accountStateService.getAccountState(address, chainId, assetChainId, assetId);
        AccountStateUnconfirmed accountStateUnconfirmed = unconfirmedStateService.getUnconfirmedInfo(address, chainId, assetChainId, assetId, accountState);
        if (isConfirmed || null == accountStateUnconfirmed) {
            rtMap.put("nonce", RPCUtil.encode(accountState.getNonce()));
            rtMap.put("nonceType", LedgerConstant.CONFIRMED_NONCE);
        } else {
            rtMap.put("nonce", RPCUtil.encode(accountStateUnconfirmed.getNonce()));
            rtMap.put("nonceType", LedgerConstant.UNCONFIRMED_NONCE);
        }
        return rtMap;
    }

    public Map<String, Object> getBalanceNonce(int chainId, int assetChainId, String address, int assetId, boolean isConfirmed) {
        AccountState accountState = accountStateService.getAccountStateReCal(address, chainId, assetChainId, assetId);
        Map<String, Object> rtMap = new HashMap<>(6);
        AccountStateUnconfirmed accountStateUnconfirmed = unconfirmedStateService.getUnconfirmedInfo(address, chainId, assetChainId, assetId, accountState);
        if (isConfirmed || null == accountStateUnconfirmed) {
            rtMap.put("nonce", RPCUtil.encode(accountState.getNonce()));
            rtMap.put("nonceType", LedgerConstant.CONFIRMED_NONCE);
            rtMap.put("available", accountState.getAvailableAmount());
        } else {
            rtMap.put("available", accountState.getAvailableAmount().subtract(accountStateUnconfirmed.getAmount()));
            rtMap.put("nonce", RPCUtil.encode(accountStateUnconfirmed.getNonce()));
            rtMap.put("nonceType", LedgerConstant.UNCONFIRMED_NONCE);
        }
        rtMap.put("freeze", accountState.getFreezeTotal());
        BigInteger permanentLocked = BigInteger.ZERO;
        BigInteger timeHeightLocked = BigInteger.ZERO;
        for (FreezeLockTimeState freezeLockTimeState : accountState.getFreezeLockTimeStates()) {
            if (LedgerConstant.PERMANENT_LOCK == freezeLockTimeState.getLockTime()) {
                permanentLocked = permanentLocked.add(freezeLockTimeState.getAmount());
            } else {
                timeHeightLocked = timeHeightLocked.add(freezeLockTimeState.getAmount());
            }
        }
        for (FreezeHeightState freezeHeightState : accountState.getFreezeHeightStates()) {
            timeHeightLocked = timeHeightLocked.add(freezeHeightState.getAmount());
        }
        rtMap.put("permanentLocked", permanentLocked);
        rtMap.put("timeHeightLocked", timeHeightLocked);
        return rtMap;
    }

}
