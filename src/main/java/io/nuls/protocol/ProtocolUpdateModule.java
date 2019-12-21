package io.nuls.protocol;

import io.nuls.ModuleState;
import io.nuls.core.ModuleE;
import io.nuls.core.NulsModule;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.Log;
import io.nuls.core.rockdb.service.RocksDBService;
import io.nuls.protocol.manager.ChainManager;
import io.nuls.protocol.model.ProtocolConfig;

import java.util.Set;

import static io.nuls.protocol.constant.Constant.PROTOCOL_CONFIG;

/**
 * 协议升级模块启动类
 *
 * @author captain
 * @version 1.0
 * @date 19-3-4 下午4:09
 */
@Component
public class ProtocolUpdateModule extends NulsModule {

    @Autowired
    public static ProtocolConfig protocolConfig;

    @Autowired
    private ChainManager chainManager;

    /**
     * 返回此模块的依赖模块
     *
     * @return
     */
    @Override
    public ModuleE[] declareDependent() {
        return new ModuleE[]{ModuleE.BL};
    }

    /**
     * 返回当前模块的描述信息
     * @return
     */
    @Override
    public ModuleE moduleInfo() {
        return ModuleE.PU;
    }


    /**
     * 初始化模块信息,比如初始化RockDB等,在此处初始化后,可在其他bean的afterPropertiesSet中使用
     */
    @Override
    public void init() {
        try {
            initDb();
            chainManager.initChain();
            ModuleHelper.init(this);
        } catch (Exception e) {
            Log.error("ProtocolUpdateBootstrap init error!");
            throw new RuntimeException(e);
        }
    }

    /**
     * 初始化数据库
     * Initialization database
     */
    private void initDb() throws Exception {
        //读取配置文件,数据存储根目录,初始化打开该目录下所有表连接并放入缓存
        RocksDBService.init(protocolConfig.getDataFolder());
        RocksDBService.createTable(PROTOCOL_CONFIG);
    }

    /**
     * 已完成spring init注入,开始启动模块
     * @return 如果启动完成返回true, 模块将进入ready状态, 若启动失败返回false, 10秒后会再次调用此方法
     */
    @Override
    public boolean doStart() {
        try {
            while (!isDependencieReady(ModuleE.BL)) {
                Thread.sleep(1000);
            }
            //启动链
            chainManager.runChain();
        } catch (Exception e) {
            Log.error("protocol module doStart error!");
            return false;
        }
        Log.info("protocol module ready");
        return true;
    }

    /**
     * 所有外部依赖进入ready状态后会调用此方法,正常启动后返回Running状态
     *
     * @return
     */
    @Override
    public ModuleState onDependenciesReady() {
        Log.info("protocol onDependenciesReady");
        return ModuleState.Running;
    }

    @Override
    public void onDependenciesReady(Module module) {

    }

    @Override
    public Set<Module> getDependencies() {
        return null;
    }

    /**
     * 某个外部依赖连接丢失后,会调用此方法,可控制模块状态,如果返回Ready,则表明模块退化到Ready状态,当依赖重新准备完毕后,将重新触发onDependenciesReady方法,若返回的状态是Running,将不会重新触发onDependenciesReady
     *
     * @param module
     * @return
     */
    @Override
    public ModuleState onDependenciesLoss(Module module) {
        return ModuleState.Running;
    }

}
