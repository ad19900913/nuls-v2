package io.nuls.transaction.utils;

import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.rockdb.service.RocksDBService;
import io.nuls.transaction.constant.TxErrorCode;

import static io.nuls.transaction.utils.LoggerUtil.LOG;

/**
 * @author: Charlie
 * @date: 2018/11/13
 */
public class DBUtil {

    public static void createTable(String name) {
        if (!RocksDBService.existTable(name)) {
            try {
                RocksDBService.createTable(name);
            } catch (Exception e) {
                LOG.error(e);
                throw new NulsRuntimeException(TxErrorCode.DB_TABLE_CREATE_ERROR);
            }
        }
    }
}
