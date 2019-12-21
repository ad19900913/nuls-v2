package io.nuls.core;

import io.nuls.ModuleState;

import java.util.Set;

public abstract class NulsModule {

    public abstract ModuleState onDependenciesLoss(Module module);

    public abstract ModuleE[] declareDependent();

    public abstract ModuleE moduleInfo();

    public abstract void init();

    public abstract boolean doStart();

    public abstract ModuleState onDependenciesReady();

    public abstract void onDependenciesReady(Module module);

    public abstract Set<Module> getDependencies();

    public static boolean isDependencieReady(ModuleE module){
        return false;
    }
}
