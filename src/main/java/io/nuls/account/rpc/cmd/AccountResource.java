package io.nuls.account.rpc.cmd;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.nuls.account.constant.AccountConstant;
import io.nuls.account.constant.AccountErrorCode;
import io.nuls.account.model.bo.Account;
import io.nuls.account.model.bo.AccountKeyStore;
import io.nuls.account.model.bo.Chain;
import io.nuls.account.model.dto.AccountKeyStoreDTO;
import io.nuls.account.model.dto.AccountOfflineDTO;
import io.nuls.account.model.dto.SimpleAccountDTO;
import io.nuls.account.service.AccountKeyStoreService;
import io.nuls.account.service.AccountService;
import io.nuls.account.service.TransactionService;
import io.nuls.account.util.AccountTool;
import io.nuls.account.util.manager.ChainManager;
import io.nuls.core.RPCUtil;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.basic.Page;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.ECKey;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.data.Address;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.FormatValidUtils;
import io.nuls.core.model.StringUtils;
import io.nuls.core.parse.JSONUtils;
import io.nuls.core.signture.BlockSignature;
import io.nuls.core.signture.P2PHKSignature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.account.util.LoggerUtil.LOG;

/**
 * @author: qinyifeng
 * @description:
 * @date: 2018/11/5
 */
@Component
public class AccountResource {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountKeyStoreService keyStoreService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private ChainManager chainManager;

    public List<String> createAccount(int chainId, int count, String password) {
        List<String> list = new ArrayList<>();
        Chain chain = chainManager.getChain(chainId);
        //创建账户
        List<Account> accountList = accountService.createAccount(chain, count, password);
        if (accountList != null) {
            accountList.forEach(account -> list.add(account.getAddress().toString()));
        }
        return list;
    }

    public List<AccountOfflineDTO> createOfflineAccount(int chainId, int count, String password) {
        List<AccountOfflineDTO> accounts = new ArrayList<>();
        Chain chain = chainManager.getChain(chainId);
        //Check parameter is correct.
        if (count <= 0 || count > AccountTool.CREATE_MAX_SIZE) {
            throw new NulsRuntimeException(AccountErrorCode.PARAMETER_ERROR);
        }
        if (!FormatValidUtils.validPassword(password)) {
            throw new NulsRuntimeException(AccountErrorCode.PASSWORD_FORMAT_WRONG);
        }

        try {
            for (int i = 0; i < count; i++) {
                Account account = AccountTool.createAccount(chain.getChainId());
                if (StringUtils.isNotBlank(password)) {
                    account.encrypt(password);
                }
                accounts.add(new AccountOfflineDTO(account));
            }
        } catch (NulsException e) {
            return List.of();
        }
        return accounts;
    }

    public String createContractAccount(int chainId) {
        Chain chain = chainManager.getChain(chainId);
        Address contractAddress = AccountTool.createContractAddress(chain.getChainId());
        return contractAddress.toString();
    }


    public SimpleAccountDTO getAccountByAddress(int chainId, String address) {
        Account account = accountService.getAccount(chainId, address);
        if (null == account) {
            return null;
        }
        return new SimpleAccountDTO(account);
    }


    public List<SimpleAccountDTO> getAccountList(int chainId) {
        List<SimpleAccountDTO> simpleAccountList = new ArrayList<>();
        List<Account> accountList = accountService.getAccountListByChain(chainId);
        if (null == accountList) {
            return List.of();
        }
        accountList.forEach(account -> simpleAccountList.add(new SimpleAccountDTO((account))));
        return simpleAccountList;
    }


    public List<String> getEncryptedAddressList(int chainId) {
        List<String> encryptedAddressList = new ArrayList<>();
        //query all accounts in a chain
        List<Account> accountList = accountService.getAccountListByChain(chainId);
        if (null == accountList) {
            return List.of();
        }
        for (Account account : accountList) {
            if (account.isEncrypted()) {
                encryptedAddressList.add(account.getAddress().getBase58());
            }
        }
        return encryptedAddressList;
    }

    public Page<String> getAddressList(int chainId, int pageNumber, int pageSize) {
        if (pageNumber < 1 || pageSize < 1) {
            throw new NulsRuntimeException(AccountErrorCode.PARAMETER_ERROR);
        }
        //根据分页参数返回账户地址列表 Returns the account address list according to paging parameters
        Page<String> page = new Page<>(pageNumber, pageSize);
        List<String> addressList = new ArrayList<>();
        //query all accounts in a chain
        List<Account> accountList = accountService.getAccountListByChain(chainId);
        if (null == accountList) {
            page.setList(addressList);
            return page;
        }
        page.setTotal(accountList.size());
        int start = (pageNumber - 1) * pageSize;
        if (start >= accountList.size()) {
            page.setList(addressList);
            return page;
        }
        int end = pageNumber * pageSize;
        if (end > accountList.size()) {
            end = accountList.size();
        }
        accountList = accountList.subList(start, end);
        //只返回账户地址 Only return to account address
        accountList.forEach(account -> addressList.add(account.getAddress().getBase58()));
        page.setList(addressList);
        return page;
    }

    public boolean removeAccount(int chainId, String address, String password) {
        return accountService.removeAccount(chainId, address, password);
    }


    public String getPubKey(int chainId, String address, String password) {
        //Get the account private key
        return accountService.getPublicKey(chainId, address, password);
    }

    /**
     * 通过账户地址和密码,查询账户私匙
     * inquire the account's private key according to the address.
     * only returns the private key of the encrypted account, and the unencrypted account does not return.
     *
     * @return
     */
    public Map<String, String> getPriKeyByAddress(int chainId, String address, String password) {
        String unencryptedPrivateKey;
        if (!AddressTool.validAddress(chainId, address)) {
            return Map.of();
        }
        Account account = accountService.getAccount(chainId, address);
        unencryptedPrivateKey = accountService.getPrivateKey(chainId, account, password);
        Map<String, String> map = new HashMap<>(AccountConstant.INIT_CAPACITY_2);
        map.put("priKey", unencryptedPrivateKey);
        map.put("pubKey", HexUtil.encode(account.getPubKey()));
        return map;
    }

    public List<String> getAllPriKey(int chainId, String password) {
        //Get the account private key
        return accountService.getAllPrivateKey(chainId, password);
    }

    /**
     * 为账户设置备注
     * set remark for accounts
     *
     * @return
     */
    public boolean setRemark(int chainId, String address, String remark) {
        return accountService.setRemark(chainId, address, remark);
    }

    /**
     * 根据私钥导入账户
     * import accounts by private key
     *
     * @return
     */
    public String importAccountByPriKey(int chainId, String password, String priKey, boolean overwrite) throws NulsException {
        Chain chain = chainManager.getChain(chainId);
        //导入账户
        Account account = accountService.importAccountByPrikey(chain, priKey, password, overwrite);
        return account.getAddress().toString();
    }


    /**
     * 根据AccountKeyStore导入账户
     * import accounts by AccountKeyStore
     *
     * @return
     */
    public String importAccountByKeystore(int chainId, String password, String keyStore, boolean overwrite) throws NulsException, IOException {
        // parse params
        Chain chain = chainManager.getChain(chainId);
        AccountKeyStoreDTO accountKeyStoreDto = JSONUtils.json2pojo(new String(RPCUtil.decode(keyStore)), AccountKeyStoreDTO.class);
        //导入账户
        Account account = accountService.importAccountByKeyStore(accountKeyStoreDto.toAccountKeyStore(), chain, password, overwrite);
        return account.getAddress().toString();
    }

    public String exportKeyStoreJson(int chainId, String address, String password) throws JsonProcessingException {
        AccountKeyStore accountKeyStore = keyStoreService.getKeyStore(chainId, address, password);
        AccountKeyStoreDTO storeDTO = new AccountKeyStoreDTO(accountKeyStore);
        return JSONUtils.obj2json(storeDTO);
    }


    public String exportAccountKeyStore(int chainId, String address, String password, String filePath) {
        return keyStoreService.backupAccountToKeyStore(filePath, chainId, address, password);
    }

    public boolean updatePassword(int chainId, String address, String password, String newPassword) {
        return accountService.changePassword(chainId, address, password, newPassword);
    }

    public String updateOfflineAccountPassword(int chainId, String address, String password, String newPassword, String priKey) {
        return accountService.changeOfflinePassword(chainId, address, priKey, password, newPassword);
    }

    public boolean validationPassword(int chainId, String address, String password) {
        //check the account is exist
        Account account = accountService.getAccount(chainId, address);
        if (null == account) {
            throw new NulsRuntimeException(AccountErrorCode.ACCOUNT_NOT_EXIST);
        }
        //verify that the account password is correct
        return account.validatePassword(password);
    }

    /**
     * 数据摘要签名
     * data digest signature
     *
     * @return
     */
    public String signDigest(int chainId, String address, String password, String dataStr) throws IOException, NulsException {
        //数据解码为字节数组
        byte[] data = RPCUtil.decode(dataStr);
        //sign digest data
        P2PHKSignature signature = accountService.signDigest(data, chainId, address, password);
        if (null == signature || signature.getSignData() == null) {
            throw new NulsRuntimeException(AccountErrorCode.SIGNATURE_ERROR);
        }
        return RPCUtil.encode(signature.serialize());
    }

    /**
     * 区块数据摘要签名
     * block data digest signature
     *
     * @return
     */
    public String signBlockDigest(int chainId, String address, String password, String dataStr) throws NulsException, IOException {
        //数据解码为字节数组
        byte[] data = RPCUtil.decode(dataStr);
        //sign digest data
        BlockSignature signature = accountService.signBlockDigest(data, chainId, address, password);
        if (null == signature || signature.getSignData() == null) {
            throw new NulsRuntimeException(AccountErrorCode.SIGNATURE_ERROR);
        }
        return RPCUtil.encode(signature.serialize());
    }

    /**
     * 验证数据签名
     * verify sign
     *
     * @return
     */
    public boolean verifySignData(String pubKey, String sig, String data) {
        return ECKey.verify(RPCUtil.decode(data), RPCUtil.decode(sig), RPCUtil.decode(pubKey));
    }

    private void errorLogProcess(Chain chain, Exception e) {
        if (chain == null) {
            LOG.error(e);
        } else {
            chain.getLogger().error(e);
        }
    }
}
