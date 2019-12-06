package io.nuls.chain.storage;


import java.math.BigInteger;

public interface ChainCirculateStorage {


    BigInteger load(String key) throws Exception;


    void save(String key, BigInteger amount) throws Exception;


}
