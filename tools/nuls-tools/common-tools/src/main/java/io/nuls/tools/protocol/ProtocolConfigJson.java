package io.nuls.tools.protocol;

import java.util.List;

public class ProtocolConfigJson {

    private short version;
    private short extend;
    private String moduleValidator;
    private String moduleCommit;
    private String moduleRollback;
    private List<TransactionConfig> validTransactions;
    private List<MessageConfig> validMessages;
    private List<ListItem> invalidTransactions;
    private List<ListItem> invalidMessages;

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public short getExtend() {
        return extend;
    }

    public void setExtend(short extend) {
        this.extend = extend;
    }

    public List<TransactionConfig> getValidTransactions() {
        return validTransactions;
    }

    public void setValidTransactions(List<TransactionConfig> validTransactions) {
        this.validTransactions = validTransactions;
    }

    public List<MessageConfig> getValidMessages() {
        return validMessages;
    }

    public void setValidMessages(List<MessageConfig> validMessages) {
        this.validMessages = validMessages;
    }

    public List<ListItem> getInvalidTransactions() {
        return invalidTransactions;
    }

    public void setInvalidTransactions(List<ListItem> invalidTransactions) {
        this.invalidTransactions = invalidTransactions;
    }

    public List<ListItem> getInvalidMessages() {
        return invalidMessages;
    }

    public void setInvalidMessages(List<ListItem> invalidMessages) {
        this.invalidMessages = invalidMessages;
    }

    public String getModuleValidator() {
        return moduleValidator;
    }

    public void setModuleValidator(String moduleValidator) {
        this.moduleValidator = moduleValidator;
    }

    public String getModuleCommit() {
        return moduleCommit;
    }

    public void setModuleCommit(String moduleCommit) {
        this.moduleCommit = moduleCommit;
    }

    public String getModuleRollback() {
        return moduleRollback;
    }

    public void setModuleRollback(String moduleRollback) {
        this.moduleRollback = moduleRollback;
    }

    public ProtocolConfigJson() {
    }
}
