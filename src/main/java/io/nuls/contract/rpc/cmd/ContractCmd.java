/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2019 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.rpc.cmd;

import io.nuls.contract.enums.BlockType;
import io.nuls.contract.enums.CmdRegisterMode;
import io.nuls.contract.helper.ContractHelper;
import io.nuls.contract.manager.*;
import io.nuls.contract.model.bo.BatchInfo;
import io.nuls.contract.model.bo.ContractTempTransaction;
import io.nuls.contract.model.dto.ContractPackageDto;
import io.nuls.contract.model.dto.ModuleCmdRegisterDto;
import io.nuls.contract.model.po.ContractOfflineTxHashPo;
import io.nuls.contract.service.ContractService;
import io.nuls.contract.util.ContractUtil;
import io.nuls.contract.util.Log;
import io.nuls.contract.util.MapUtil;
import io.nuls.contract.vm.program.*;
import io.nuls.core.RPCUtil;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.basic.Result;
import io.nuls.core.constant.TxType;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.data.CoinData;
import io.nuls.core.data.CoinTo;
import io.nuls.core.data.Transaction;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nuls.contract.constant.ContractConstant.*;
import static io.nuls.contract.constant.ContractErrorCode.*;
import static io.nuls.contract.util.ContractUtil.checkVmResultAndReturn;
import static io.nuls.contract.util.ContractUtil.getSuccess;
import static io.nuls.core.constant.CommonCodeConstanst.DATA_ERROR;
import static io.nuls.core.constant.CommonCodeConstanst.PARAMETER_ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * @author: PierreLuo
 * @date: 2019-03-11
 */
@Component
public class ContractCmd {

    @Autowired
    private ContractService contractService;
    @Autowired
    private ContractHelper contractHelper;
    @Autowired
    private ContractTxProcessorManager contractTxProcessorManager;
    @Autowired
    private ContractTxValidatorManager contractTxValidatorManager;
    @Autowired
    private CmdRegisterManager cmdRegisterManager;

    public boolean batchBegin(int chainId, int blockType, long blockHeight, long blockTime, String packingAddress, String preStateRoot) {
        ChainManager.chainHandle(chainId, blockType);
        Result result = contractService.begin(chainId, blockHeight, blockTime, packingAddress, preStateRoot);
        return result.isSuccess();
    }

    public boolean invokeContractOneByOne(int chainId, int blockType, ContractTempTransaction tx) {
        ChainManager.chainHandle(chainId, blockType);
        String hash = tx.getHash().toHex();
        if (!contractHelper.getChain(chainId).getBatchInfo().checkGasCostTotal(hash)) {
            Log.warn("Exceed tx count [500] or gas limit of block [12,000,000 gas], the contract transaction [{}] revert to package queue.", hash);
            return false;
        }
        Result result = contractService.invokeContractOneByOne(chainId, tx);
        return result.isSuccess();
    }

    public boolean batchBeforeEnd(int chainId, int blockType, long blockHeight) {
        ChainManager.chainHandle(chainId, blockType);
        Result result = contractService.beforeEnd(chainId, blockHeight);
        Log.info("[Before End Result] contract batch, result is {}", result.toString());
        return result.isSuccess();
    }

    public Map<String, Object> batchEnd(int chainId, long blockHeight) {
        ChainManager.chainHandle(chainId, BlockType.VERIFY_BLOCK.type());
        Log.info("[End Contract Batch] contract batch request start, height is {}", blockHeight);
        Result result = contractService.end(chainId, blockHeight);
        if (result.isFailed()) {
            return Map.of();
        }
        BatchInfo batchInfo = contractHelper.getChain(chainId).getBatchInfo();
        List<String> pendingTxHashList = batchInfo.getPendingTxHashList();
        ContractPackageDto dto = (ContractPackageDto) result.getData();
        List<String> resultTxDataList = dto.getResultTxList();
        Map<String, Object> resultMap = MapUtil.createHashMap(2);
        resultMap.put("stateRoot", RPCUtil.encode(dto.getStateRoot()));
        resultMap.put("txList", resultTxDataList);
        // 存放未处理的交易
        resultMap.put("pendingTxHashList", pendingTxHashList);
        Log.info("[End Contract Batch] Gas total cost is [{}], packaging blockHeight is [{}], packaging StateRoot is [{}]", batchInfo.getGasCostTotal(), blockHeight, RPCUtil.encode(dto.getStateRoot()));
        return resultMap;
    }

    public Map<String, Object> packageBatchEnd(int chainId, long blockHeight) {
        ChainManager.chainHandle(chainId, BlockType.PACKAGE_BLOCK.type());
        Log.info("[End Package Contract Batch] contract batch request start, height is {}", blockHeight);
        Result result = contractService.packageEnd(chainId, blockHeight);
        if (result.isFailed()) {
            return Map.of();
        }
        BatchInfo batchInfo = contractHelper.getChain(chainId).getBatchInfo();
        List<String> pendingTxHashList = batchInfo.getPendingTxHashList();
        ContractPackageDto dto = (ContractPackageDto) result.getData();
        List<String> resultTxDataList = dto.getResultTxList();
        Map<String, Object> resultMap = MapUtil.createHashMap(2);
        resultMap.put("stateRoot", RPCUtil.encode(dto.getStateRoot()));
        resultMap.put("txList", resultTxDataList);
        // 存放未处理的交易
        resultMap.put("pendingTxHashList", pendingTxHashList);
        Log.info("[End Package Contract Batch] Gas total cost is [{}], packaging blockHeight is [{}], packaging StateRoot is [{}]", batchInfo.getGasCostTotal(), blockHeight, RPCUtil.encode(dto.getStateRoot()));
        return resultMap;
    }

    public List<String> contractOfflineTxHashList(int chainId, String blockHash) throws NulsException {
        ChainManager.chainHandle(chainId);
        Result<ContractOfflineTxHashPo> result = contractService.getContractOfflineTxHashList(chainId, blockHash);
        if (result.isFailed()) {
            return List.of();
        }
        List<byte[]> hashList = result.getData().getHashList();
        List<String> resultList = new ArrayList<>(hashList.size());
        for (byte[] hash : hashList) {
            resultList.add(RPCUtil.encode(hash));
        }
        return resultList;
    }

    public boolean initialAccountToken(int chainId, String address) throws NulsException {
        ChainManager.chainHandle(chainId);
        if (!AddressTool.validAddress(chainId, address)) {
            throw new NulsException(ADDRESS_ERROR);
        }
        ContractTokenBalanceManager contractTokenBalanceManager = contractHelper.getChain(chainId).getContractTokenBalanceManager();
        if (contractTokenBalanceManager == null) {
            throw new NulsException(DATA_ERROR);
        }
        Result result = contractTokenBalanceManager.initAllTokensByImportAccount(address);
        return result.isSuccess();
    }

    /**
     * 其他模块向合约模块注册可被合约调用的命令
     *
     * @return
     */
    public boolean registerCmdForContract(int chainId, ModuleCmdRegisterDto moduleCmdRegisterDto) {
        ChainManager.chainHandle(chainId);
        Result result = cmdRegisterManager.registerCmd(moduleCmdRegisterDto);
        return result.isSuccess();
    }

    /**
     * 共识奖励收益地址是合约地址时，会触发合约的_payable(String[][] args)方法，参数是节点收益地址明细
     * args[0] = new String[]{address, amount}
     * ...
     *
     * @return 变化后的stateRoot
     */
    public String triggerPayableForConsensusContract(int chainId, String stateRoot, long blockHeight, String contractAddress, Transaction tx) throws NulsException {
        ChainManager.chainHandle(chainId);

        Long packageHeight = blockHeight + 1;
        if (Log.isDebugEnabled()) {
            Log.debug("contract trigger payable for consensus rewarding, blockHeight is {}, preStateRoot is {}", packageHeight, stateRoot);
        }
        boolean hasAgentContract = StringUtils.isNotBlank(contractAddress);
        if (hasAgentContract && !AddressTool.validAddress(chainId, contractAddress)) {
            throw new NulsException(ADDRESS_ERROR);
        }

        if (TxType.COIN_BASE != tx.getType()) {
            throw new NulsException(PARAMETER_ERROR);
        }
        CoinData coinData = tx.getCoinDataInstance();
        List<CoinTo> toList = coinData.getTo();
        int toListSize = toList.size();
        if (toListSize == 0) {
            return stateRoot;
        }

        byte[] stateRootBytes = RPCUtil.decode(stateRoot);
        // 获取VM执行器
        ProgramExecutor programExecutor = contractHelper.getProgramExecutor(chainId);
        // 执行VM
        ProgramExecutor batchExecutor = programExecutor.begin(stateRootBytes);

        BigInteger agentValue = BigInteger.ZERO;
        BigInteger value;

        byte[] contractAddressBytes = null;
        if (hasAgentContract) {
            contractAddressBytes = AddressTool.getAddress(contractAddress);
        }

        String[][] agentArgs = new String[toListSize][];
        String[][] depositArgs = new String[1][];
        int i = 0;
        if (hasAgentContract) {
            i++;
        }
        byte[] address;
        String[] element;
        Result result;
        for (CoinTo to : toList) {
            address = to.getAddress();
            value = to.getAmount();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                Log.error("address [{}] - error amount [{}]", AddressTool.getStringAddressByBytes(address), value.toString());
                throw new NulsException(PARAMETER_ERROR);
            }

            if (hasAgentContract && Arrays.equals(address, contractAddressBytes)) {
                agentValue = to.getAmount();
                continue;
            }
            element = new String[]{AddressTool.getStringAddressByBytes(address), value.toString()};
            // 当CoinBase交易中出现委托节点的合约地址时，触发这个合约的_payable(String[][] args)方法，参数为这个合约地址的收益金额 eg. [[address, amount]]
            if (AddressTool.validContractAddress(address, chainId)) {
                depositArgs[0] = element;
                result = this.callDepositContract(chainId, address, value, blockHeight, depositArgs, batchExecutor, stateRootBytes);
                if (result.isFailed()) {
                    Log.error("deposit contract address [{}] trigger payable error [{}], blockHeight is {}", AddressTool.getStringAddressByBytes(address), extractMsg(result), packageHeight);
                }
            }
            agentArgs[i++] = element;
        }
        // 当这个区块的打包节点的收益地址是合约地址时，触发这个合约的_payable(String[][] args)方法，参数是这个区块的所有收益地址明细 eg. [[address, amount], [address, amount], ...]
        if (hasAgentContract) {
            // 把合约地址的收益放在参数列表的首位
            agentArgs[0] = new String[]{contractAddress, agentValue.toString()};
            result = this.callAgentContract(chainId, contractAddressBytes, agentValue, blockHeight, agentArgs, batchExecutor, stateRootBytes);
            if (result.isFailed()) {
                Log.error("agent contract address [{}] trigger payable error [{}], blockHeight is {}", AddressTool.getStringAddressByBytes(contractAddressBytes), extractMsg(result), packageHeight);
            }
        }

        batchExecutor.commit();
        byte[] newStateRootBytes = batchExecutor.getRoot();
        Log.info("contract trigger payable for consensus rewarding, blockHeight is {}, preStateRoot is {}, currentStateRoot is {}", packageHeight, stateRoot, HexUtil.encode(newStateRootBytes));
        return RPCUtil.encode(newStateRootBytes);
    }

    private String extractMsg(Result result) {
        if (result == null) {
            return EMPTY;
        }
        String msg = result.getMsg();
        return msg != null ? msg : result.getErrorCode().getMsg();
    }

    private Result callAgentContract(int chainId, byte[] contractAddressBytes, BigInteger value, Long blockHeight, String[][] args, ProgramExecutor batchExecutor, byte[] stateRootBytes) {
        if (Log.isDebugEnabled()) {
            Log.debug("agent contract trigger payable for consensus rewarding, blockHeight is {}, contractAddress is {}, reward detail is {}",
                    blockHeight + 1, AddressTool.getStringAddressByBytes(contractAddressBytes), ContractUtil.toString(args));
        }
        return this.callConsensusContract(chainId, contractAddressBytes, value, blockHeight, args, batchExecutor, stateRootBytes, true);
    }

    private Result callDepositContract(int chainId, byte[] contractAddressBytes, BigInteger value, Long blockHeight, String[][] args, ProgramExecutor batchExecutor, byte[] stateRootBytes) {
        if (Log.isDebugEnabled()) {
            Log.debug("deposit contract trigger payable for consensus rewarding, blockHeight is {}, contractAddress is {}, reward is {}",
                    blockHeight + 1, AddressTool.getStringAddressByBytes(contractAddressBytes), value.toString());
        }
        return this.callConsensusContract(chainId, contractAddressBytes, value, blockHeight, args, batchExecutor, stateRootBytes, false);
    }

    private Result callConsensusContract(int chainId, byte[] contractAddressBytes, BigInteger value, Long blockHeight, String[][] args,
                                         ProgramExecutor batchExecutor, byte[] stateRootBytes, boolean isAgentContract) {
        // 验证此合约是否接受直接转账
        ProgramMethod methodInfo = contractHelper.getMethodInfoByContractAddress(chainId, stateRootBytes,
                BALANCE_TRIGGER_METHOD_NAME, BALANCE_TRIGGER_FOR_CONSENSUS_CONTRACT_METHOD_DESC, contractAddressBytes);
        if (methodInfo == null) {
            Log.error("chainId: {}, contractAddress: {}, stateRoot: {}", chainId,
                    AddressTool.getStringAddressByBytes(contractAddressBytes),
                    HexUtil.encode(stateRootBytes));
            return Result.getFailed(CONTRACT_METHOD_NOT_EXIST);
        }
        if (!methodInfo.isPayable()) {
            return Result.getFailed(CONTRACT_NO_ACCEPT_DIRECT_TRANSFER);
        }
        // 组装VM执行数据
        ProgramCall programCall = new ProgramCall();
        programCall.setContractAddress(contractAddressBytes);
        programCall.setSender(null);
        programCall.setNumber(blockHeight);
        programCall.setValue(value);
        programCall.setPrice(CONTRACT_MINIMUM_PRICE);
        if (isAgentContract) {
            programCall.setGasLimit(AGENT_CONTRACT_CONSTANT_GASLIMIT);
        } else {
            programCall.setGasLimit(DEPOSIT_CONTRACT_CONSTANT_GASLIMIT);
        }
        programCall.setMethodName(BALANCE_TRIGGER_METHOD_NAME);
        programCall.setMethodDesc(BALANCE_TRIGGER_FOR_CONSENSUS_CONTRACT_METHOD_DESC);
        programCall.setArgs(args);

        ProgramExecutor track = batchExecutor.startTracking();
        ProgramResult programResult = track.call(programCall);
        if (!programResult.isSuccess()) {
            Log.error("contractAddress[{}], errorMessage[{}], errorStackTrace[{}]", AddressTool.getStringAddressByBytes(contractAddressBytes),
                    programResult.getErrorMessage(), programResult.getStackTrace());
            Result result = Result.getFailed(DATA_ERROR);
            result.setMsg(ContractUtil.simplifyErrorMsg(programResult.getErrorMessage()));
            result = checkVmResultAndReturn(programResult.getErrorMessage(), result);
            return result;
        }
        // 限制不能token转账、不能发送事件、不能内部转账、不能内部调用合约、不能产生新交易
        List<String> events = programResult.getEvents();
        List<ProgramTransfer> transfers = programResult.getTransfers();
        List<ProgramInternalCall> internalCalls = programResult.getInternalCalls();
        List<ProgramInvokeRegisterCmd> invokeRegisterCmds = programResult.getInvokeRegisterCmds();
        int size = events.size() + transfers.size() + internalCalls.size();
        if (size > 0) {
            return Result.getFailed(TRIGGER_PAYABLE_FOR_CONSENSUS_CONTRACT_ERROR);
        }
        for (ProgramInvokeRegisterCmd registerCmd : invokeRegisterCmds) {
            if (CmdRegisterMode.NEW_TX.equals(registerCmd.getCmdRegisterMode())) {
                return Result.getFailed(TRIGGER_PAYABLE_FOR_CONSENSUS_CONTRACT_ERROR);
            }
        }

        track.commit();
        return getSuccess();
    }

}
