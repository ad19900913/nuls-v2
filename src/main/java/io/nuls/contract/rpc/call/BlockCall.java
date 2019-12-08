/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2019 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.contract.rpc.call;

import io.nuls.block.rpc.BlockResource;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.data.NulsHash;

/**
 * @author: PierreLuo
 * @date: 2019-02-27
 */
public class BlockCall {

    @Autowired
    private static BlockResource blockResource;

    public static long getLatestHeight(int chainId) {
        return blockResource.latestHeight(chainId);
    }

    public static BlockHeader getLatestBlockHeader(int chainId) {
        return blockResource.latestBlockHeader(chainId);
    }

    public static BlockHeader getBlockHeader(int chainId, long height) {
        return blockResource.getBlockHeaderByHeight(chainId, height);
    }

    public static BlockHeader getBlockHeader(int chainId, String hash) {
        return blockResource.getBlockHeaderByHash(chainId, NulsHash.fromHex(hash));
    }
}
