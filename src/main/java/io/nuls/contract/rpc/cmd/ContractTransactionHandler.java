package io.nuls.contract.rpc.cmd;

import io.nuls.contract.manager.ChainManager;
import io.nuls.contract.manager.ContractTxValidatorManager;
import io.nuls.contract.model.tx.CallContractTransaction;
import io.nuls.contract.model.tx.CreateContractTransaction;
import io.nuls.contract.model.tx.DeleteContractTransaction;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Service;
import io.nuls.core.exception.NulsException;

import static io.nuls.core.constant.TxType.*;

@Service
public class ContractTransactionHandler {

    @Autowired
    private ContractTxValidatorManager contractTxValidatorManager;

    public boolean createValidator(int chainId, CreateContractTransaction tx) throws NulsException {
        ChainManager.chainHandle(chainId);
        if (tx.getType() != CREATE_CONTRACT) {
            return false;
        }
        Result result = contractTxValidatorManager.createValidator(chainId, tx);
        return result.isSuccess();
    }

    public boolean callValidator(int chainId, CallContractTransaction tx) throws NulsException {
        ChainManager.chainHandle(chainId);
        if (tx.getType() != CALL_CONTRACT) {
            return false;
        }
        Result result = contractTxValidatorManager.callValidator(chainId, tx);
        return result.isSuccess();
    }

    public boolean deleteValidator(int chainId, DeleteContractTransaction tx) throws NulsException {
        ChainManager.chainHandle(chainId);
        if (tx.getType() != DELETE_CONTRACT) {
            return false;
        }
        Result result = contractTxValidatorManager.deleteValidator(chainId, tx);
        return result.isSuccess();
    }

}
