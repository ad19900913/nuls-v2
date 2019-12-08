/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.block.rpc;

import io.nuls.block.manager.ContextManager;
import io.nuls.block.model.ChainContext;
import io.nuls.block.service.BlockService;
import io.nuls.block.utils.SmallBlockCacher;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.Block;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.data.NulsHash;
import io.nuls.core.data.po.BlockHeaderPo;
import io.nuls.core.log.logback.NulsLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.block.constant.BlockForwardEnum.ERROR;

/**
 * 区块管理模块的对外接口类
 *
 * @author captain
 * @version 1.0
 * @date 18-11-9 下午2:04
 */
@Component
public class BlockResource {
    @Autowired
    private BlockService service;

    /**
     * 获取最新主链高度
     *
     * @param chainId
     * @return
     */
    public Map<String, Long> info(int chainId) {
        Map<String, Long> responseData = new HashMap<>(2);
        ChainContext context = ContextManager.getContext(chainId);
        responseData.put("networkHeight", context.getNetworkHeight());
        responseData.put("localHeight", context.getLatestHeight());
        return responseData;
    }

    /**
     * 获取最新主链高度
     *
     * @param chainId
     * @return
     */
    public long latestHeight(int chainId) {
        ChainContext context = ContextManager.getContext(chainId);
        return context.getLatestHeight();
    }

    /**
     * 获取最新区块头
     *
     * @param chainId
     * @return
     */
    public BlockHeader latestBlockHeader(int chainId) {
        return service.getLatestBlockHeader(chainId);
    }

    /**
     * 获取最新区块头PO
     *
     * @param chainId
     * @return
     */
    public BlockHeaderPo latestBlockHeaderPo(int chainId) {
        return service.getLatestBlockHeaderPo(chainId);
    }

    /**
     * 获取最新区块
     *
     * @param chainId
     * @return
     */
    public Block bestBlock(int chainId) {
        return service.getLatestBlock(chainId);
    }

    /**
     * 根据高度获取区块头
     *
     * @param chainId
     * @param height
     * @return
     */
    public BlockHeader getBlockHeaderByHeight(int chainId, long height) {
        return service.getBlockHeader(chainId, height);
    }

    /**
     * 根据高度获取区块头
     *
     * @param chainId
     * @param height
     * @return
     */
    public BlockHeaderPo getBlockHeaderPoByHeight(int chainId, long height) {
        return service.getBlockHeaderPo(chainId, height);
    }

    /**
     * 获取最新若干个区块头
     *
     * @return
     */
    public List<BlockHeader> getLatestBlockHeaders(int chainId, int size) {
        long latestHeight = ContextManager.getContext(chainId).getLatestHeight();
        long startHeight = latestHeight - size + 1;
        startHeight = startHeight < 0 ? 0 : startHeight;
        return service.getBlockHeader(chainId, startHeight, latestHeight);
    }

    /**
     * 获取最新若干轮区块头,提供给POC共识模块使用
     *
     * @return
     */
    public List<BlockHeader> getRoundBlockHeaders(int chainId, long height, int round) {
        height = height - 1 < 0 ? 0 : height - 1;
        return service.getBlockHeaderByRound(chainId, height, round);
    }

    /**
     * 获取最新若干轮区块头,提供给POC共识模块使用
     *
     * @return
     */
    public List<BlockHeader> getLatestRoundBlockHeaders(int chainId, int round) {
        ChainContext context = ContextManager.getContext(chainId);
        return service.getBlockHeaderByRound(chainId, context.getLatestHeight(), round);
    }

    /**
     * 获取区块头,给协议升级模块使用
     *
     * @return
     */
    public List<BlockHeader> getBlockHeadersForProtocol(int chainId, int interval) {
        ChainContext context = ContextManager.getContext(chainId);
        long latestHeight = context.getLatestHeight();
        if (latestHeight % interval == 0) {
            return List.of();
        }
        return service.getBlockHeader(chainId, latestHeight - (latestHeight % interval) + 1, latestHeight);
    }

    /**
     * 根据高度区间获取区块头
     *
     * @return
     */
    public List<BlockHeader> getBlockHeadersByHeightRange(int chainId, long begin, long end) {
        return service.getBlockHeader(chainId, begin, end);
    }

    /**
     * 根据高度获取区块
     *
     * @return
     */
    public Block getBlockByHeight(int chainId, long height) {
        return service.getBlock(chainId, height);
    }

    /**
     * 根据hash获取区块头
     *
     * @return
     */
    public BlockHeader getBlockHeaderByHash(int chainId, NulsHash hash) {
        return service.getBlockHeader(chainId, hash);
    }

    /**
     * 根据hash获取区块头
     *
     * @return
     */
    public BlockHeaderPo getBlockHeaderPoByHash(int chainId, NulsHash hash) {
        return service.getBlockHeaderPo(chainId, hash);
    }

    /**
     * 根据hash获取区块
     *
     * @return
     */
    public Block getBlockByHash(int chainId, NulsHash hash) {
        return service.getBlock(chainId, hash);
    }

    /**
     * 接收新打包区块
     * 1.保存区块
     * 2.广播区块
     *
     * @return
     */
    public boolean receivePackingBlock(int chainId, Block block) {
        ChainContext context = ContextManager.getContext(chainId);
        NulsLogger logger = context.getLogger();
        logger.debug("recieve block from local node, height:" + block.getHeader().getHeight() + ", hash:" + block.getHeader().getHash());
        if (service.saveBlock(chainId, block, 1, true, true, false)) {
            return true;
        } else {
            SmallBlockCacher.setStatus(chainId, block.getHeader().getHash(), ERROR);
            return false;
        }
    }

    /**
     * 获取当前运行状态
     * status-0:同步
     * status-1:正常运行
     *
     * @return
     */
    public int getStatus(int chainId) {
        ChainContext context = ContextManager.getContext(chainId);
        int status;
        switch (context.getStatus()) {
            case INITIALIZING:
            case WAITING:
            case SYNCHRONIZING:
                status = 0;
                break;
            default:
                status = 1;
        }
        return status;
    }
}
