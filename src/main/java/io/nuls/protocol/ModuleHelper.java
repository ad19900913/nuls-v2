package io.nuls.protocol;

import io.nuls.contract.SmartContractModule;
import io.nuls.core.NulsModule;
import io.nuls.core.log.Log;
import io.nuls.crosschain.nuls.CrossChainModule;

import java.util.Set;

/**
 * 辅助类，判断节点是够支持一些额外的功能
 *
 * @author captain
 * @version 1.0
 * @date 2019/4/25 16:44
 */
public class ModuleHelper {

    /**
     * 是否支持协议升级功能
     */
    private static boolean supportProtocolUpdate;
    /**
     * 是否支持智能合约功能
     */
    private static boolean supportSmartContract;
    /**
     * 是否支持跨链功能
     */
    private static boolean supportCrossChain;

    public static boolean isSupportProtocolUpdate() {
        return supportProtocolUpdate;
    }

    public static boolean isSupportSmartContract() {
        return supportSmartContract;
    }

    public static boolean isSupportCrossChain() {
        return supportCrossChain;
    }

    public static void init(NulsModule module) {
        supportProtocolUpdate = enableProtocolUpdate(module);
        supportSmartContract = enableSmartContract(module);
        supportCrossChain = enableCrossChain(module);
        Log.info("----------------ModuleHelper----------------");
        Log.info("module-" + module.toString());
        Log.info("supportProtocolUpdate-" + supportProtocolUpdate);
        Log.info("supportSmartContract-" + supportSmartContract);
        Log.info("supportCrossChain-" + supportCrossChain);
    }

    private static boolean enableProtocolUpdate(NulsModule module) {
        NulsModule m = new ProtocolUpdateModule();
        Set<Module> dependencies = module.getDependencies();
        return module.moduleInfo().equals(m) || dependencies.contains(m);
    }

    private static boolean enableSmartContract(NulsModule module) {
        NulsModule m = new SmartContractModule();
        Set<Module> dependencies = module.getDependencies();
        return module.moduleInfo().equals(m) || dependencies.contains(m);
    }

    private static boolean enableCrossChain(NulsModule module) {
        NulsModule m = new CrossChainModule();
        Set<Module> dependencies = module.getDependencies();
        return module.moduleInfo().equals(m) || dependencies.contains(m);
    }

}
