package io.nuls.crosschain.nuls;

import io.nuls.AddressPrefixDatas;
import io.nuls.ModuleState;
import io.nuls.core.ModuleE;
import io.nuls.core.NulsModule;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.Log;
import io.nuls.core.rockdb.service.RocksDBService;
import io.nuls.crosschain.base.message.RegisteredChainMessage;
import io.nuls.crosschain.nuls.constant.NulsCrossChainConfig;
import io.nuls.crosschain.nuls.model.bo.Chain;
import io.nuls.crosschain.nuls.rpc.call.AccountCall;
import io.nuls.crosschain.nuls.rpc.call.ChainManagerCall;
import io.nuls.crosschain.nuls.rpc.call.NetWorkCall;
import io.nuls.crosschain.nuls.srorage.RegisteredCrossChainService;
import io.nuls.crosschain.nuls.utils.manager.ChainManager;
import io.nuls.protocol.ProtocolGroupManager;
import io.nuls.protocol.RegisterHelper;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Set;

import static io.nuls.crosschain.nuls.constant.NulsCrossChainConstant.*;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * 跨链模块启动类
 * Cross Chain Module Startup and Initialization Management
 *
 * @author tag
 * 2019/4/10
 */
@Component
public class CrossChainModule extends NulsModule {
    @Autowired
    private NulsCrossChainConfig nulsCrossChainConfig;
    @Autowired
    private RegisteredCrossChainService registeredCrossChainService;

    @Autowired
    private ChainManager chainManager;

    /**
     * 初始化模块，比如初始化RockDB等，在此处初始化后，可在其他bean的afterPropertiesSet中使用
     * 在onStart前会调用此方法
     */
    @Override
    public void init() {
        try {
            initSys();
            //增加地址工具类初始化
            AddressTool.init(new AddressPrefixDatas());
            initDB();
            /**
             * 添加RPC接口目录
             * Add RPC Interface Directory
             * */
            chainManager.initChain();
        } catch (Exception e) {
            Log.error(e);
        }
    }

    @Override
    public ModuleE[] declareDependent() {
        if (nulsCrossChainConfig.getMainChainId() == nulsCrossChainConfig.getChainId()) {
            return new ModuleE[]{
                    ModuleE.NW,
                    ModuleE.TX,
                    ModuleE.CM,
                    ModuleE.AC,
                    ModuleE.CS,
                    ModuleE.LG,
                    ModuleE.BL
            };
        } else {
            return new ModuleE[]{
                    ModuleE.NW,
                    ModuleE.TX,
                    ModuleE.AC,
                    ModuleE.CS,
                    ModuleE.LG,
                    ModuleE.BL
            };
        }
    }

    @Override
    public ModuleE moduleInfo() {
        return null;
    }

    @Override
    public boolean doStart() {
        try {
            while (!isDependencieReady(ModuleE.NW) || !isDependencieReady(ModuleE.TX) || !isDependencieReady(ModuleE.CS)) {
                Log.debug("wait depend modules ready");
                Thread.sleep(2000L);
            }
            chainManager.runChain();
            return true;
        } catch (Exception e) {
            Log.error(e);
            return false;
        }
    }

    @Override
    public void onDependenciesReady(Module module) {
        try {
            /*
             * 注册交易
             * Registered transactions
             */
            if (module.getName().equals(ModuleE.TX.abbr)) {
                for (Integer chainId : chainManager.getChainMap().keySet()) {
                    RegisterHelper.registerTx(chainId, ProtocolGroupManager.getCurrentProtocol(chainId));
                }
            }
            /*
             * 注册协议,如果为非主网则需激活跨链网络
             */
            if (ModuleE.NW.abbr.equals(module.getName())) {
                RegisterHelper.registerMsg(ProtocolGroupManager.getOneProtocol());
                for (Chain chain : chainManager.getChainMap().values()) {
                    if (!chain.isMainChain()) {
                        NetWorkCall.activeCrossNet(chain.getChainId(), chain.getConfig().getMaxOutAmount(), chain.getConfig().getMaxInAmount(), nulsCrossChainConfig.getCrossSeedIps());
                    }
                }
            }
            /*
             * 如果为主网，向链管理模块过去完整的跨链注册信息
             */
            if (nulsCrossChainConfig.isMainNet() && (ModuleE.CM.abbr.equals(module.getName()))) {
                RegisteredChainMessage registeredChainMessage = registeredCrossChainService.get();
                if (registeredChainMessage != null && registeredChainMessage.getChainInfoList() != null) {
                    chainManager.setRegisteredCrossChainList(registeredChainMessage.getChainInfoList());
                } else {
                    registeredChainMessage = ChainManagerCall.getRegisteredChainInfo();
                    registeredCrossChainService.save(registeredChainMessage);
                    chainManager.setRegisteredCrossChainList(registeredChainMessage.getChainInfoList());

                }
            }

            /*
             * 如果为账户模块启动，向账户模块发送链前缀
             */
            if (ModuleE.AC.abbr.equals(module.getName())) {
                AccountCall.addAddressPrefix(chainManager.getPrefixList());
            }
        } catch (Exception e) {
            Log.error(e);
        }
    }

    @Override
    public Set<Module> getDependencies() {
        return null;
    }

    @Override
    public ModuleState onDependenciesReady() {
        Log.debug("cc onDependenciesReady");
        return ModuleState.Running;
    }

    @Override
    public ModuleState onDependenciesLoss(Module dependenciesModule) {
        return ModuleState.Ready;
    }

    /**
     * 初始化系统编码
     * Initialization System Coding
     */
    private void initSys() throws Exception {
        System.setProperty(SYS_FILE_ENCODING, UTF_8.name());
        Field charset = Charset.class.getDeclaredField("defaultCharset");
        charset.setAccessible(true);
        charset.set(null, UTF_8);
    }

    /**
     * 初始化数据库
     * Initialization database
     */
    private void initDB() throws Exception {
        RocksDBService.init(nulsCrossChainConfig.getDataFolder());
        RocksDBService.createTable(DB_NAME_CONSUME_LANGUAGE);
        RocksDBService.createTable(DB_NAME_CONSUME_CONGIF);
        /*
            已注册跨链的链信息操作表
            Registered Cross-Chain Chain Information Operating Table
            key：RegisteredChain
            value:已注册链信息列表
            */
        RocksDBService.createTable(DB_NAME_REGISTERED_CHAIN);
    }

}
