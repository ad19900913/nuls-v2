package io.nuls.crosschain.nuls.rpc.call;

import io.nuls.block.rpc.BlockResource;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.data.BlockHeader;
import io.nuls.crosschain.nuls.model.bo.Chain;
import io.nuls.crosschain.nuls.servive.BlockService;

import java.util.HashMap;
import java.util.Map;

/**
 * 调用其他模块跟交易相关的接口
 *
 * @author: tag
 * @date: 2019/4/12
 */
public class BlockCall {

    @Autowired
    private static BlockResource blockResource;

    private static BlockService blockService = SpringLiteContext.getBean(BlockService.class);

    /**
     * 区块最新高度
     */
    public static void subscriptionNewBlockHeight(Chain chain) {
        Map<String, Object> params = new HashMap<>();
        params.put("chainId", chain.getChainId());
        params.put("height", blockResource.latestHeight(chain.getChainId()));
        blockService.newBlockHeight(params);
    }

    /**
     * 查询区块状态
     */
    public static int getBlockStatus(Chain chain) {
        return blockResource.getStatus(chain.getChainId());
    }

    /**
     * 查询最新区块高度
     */
    public static BlockHeader getLatestBlockHeader(Chain chain) {
        return blockResource.latestBlockHeader(chain.getChainId());
    }
}
