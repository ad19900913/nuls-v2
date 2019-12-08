/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.network.rpc.call.impl;

import io.nuls.block.rpc.BlockResource;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.BlockHeader;
import io.nuls.network.model.dto.BestBlockInfo;
import io.nuls.network.rpc.call.BlockRpcService;

/**
 * 调用区块模块的RPC接口
 *
 * @author lan
 * @description
 * @date 2018/12/07
 **/
@Component
public class BlockRpcServiceImpl implements BlockRpcService {

    @Autowired
    private static BlockResource blockResource;

    /**
     * 获取最近区块高度与hash
     *
     * @param chainId chainId
     * @return
     */
    @Override
    public BestBlockInfo getBestBlockHeader(int chainId) {
        BestBlockInfo bestBlockInfo = new BestBlockInfo();
        BlockHeader blockHeader = blockResource.latestBlockHeader(chainId);
        bestBlockInfo.setBlockHeight(blockHeader.getHeight());
        bestBlockInfo.setHash(blockHeader.getHash().toHex());
        return bestBlockInfo;
    }
}
