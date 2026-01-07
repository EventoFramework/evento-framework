package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.demo.api.view.enums.FailStage;



public class TimeoutCommand extends ServiceCommand {
    private long millis;
    private long times;

    private boolean beforeWriteLocal;
    private boolean afterWriteLocal;
    private boolean afterBrokerRead;
    private boolean beforeBrokerSend;
    private boolean afterBrokerSend;
    private boolean afterReadRemote;
    private boolean beforeWriteRemote;

    private boolean beforeWriteIgnoreLocal;
    private boolean beforeBrokerIgnoreSend;
    private boolean beforeWriteIgnoreRemote;


    public long getMillis() {
        return millis;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public long getTimes() {
        return times;
    }

    public void setTimes(long times) {
        this.times = times;
    }

    public boolean isBeforeWriteLocal() {
        return beforeWriteLocal;
    }

    public void setBeforeWriteLocal(boolean beforeWriteLocal) {
        this.beforeWriteLocal = beforeWriteLocal;
    }

    public boolean isAfterWriteLocal() {
        return afterWriteLocal;
    }

    public void setAfterWriteLocal(boolean afterWriteLocal) {
        this.afterWriteLocal = afterWriteLocal;
    }

    public boolean isAfterBrokerRead() {
        return afterBrokerRead;
    }

    public void setAfterBrokerRead(boolean afterBrokerRead) {
        this.afterBrokerRead = afterBrokerRead;
    }

    public boolean isBeforeBrokerSend() {
        return beforeBrokerSend;
    }

    public void setBeforeBrokerSend(boolean beforeBrokerSend) {
        this.beforeBrokerSend = beforeBrokerSend;
    }

    public boolean isAfterBrokerSend() {
        return afterBrokerSend;
    }

    public void setAfterBrokerSend(boolean afterBrokerSend) {
        this.afterBrokerSend = afterBrokerSend;
    }

    public boolean isAfterReadRemote() {
        return afterReadRemote;
    }

    public void setAfterReadRemote(boolean afterReadRemote) {
        this.afterReadRemote = afterReadRemote;
    }

    public boolean isBeforeWriteRemote() {
        return beforeWriteRemote;
    }

    public void setBeforeWriteRemote(boolean beforeWriteRemote) {
        this.beforeWriteRemote = beforeWriteRemote;
    }

    public boolean isBeforeWriteIgnoreLocal() {
        return beforeWriteIgnoreLocal;
    }

    public void setBeforeWriteIgnoreLocal(boolean beforeWriteIgnoreLocal) {
        this.beforeWriteIgnoreLocal = beforeWriteIgnoreLocal;
    }

    public boolean isBeforeBrokerIgnoreSend() {
        return beforeBrokerIgnoreSend;
    }

    public void setBeforeBrokerIgnoreSend(boolean beforeBrokerIgnoreSend) {
        this.beforeBrokerIgnoreSend = beforeBrokerIgnoreSend;
    }

    public boolean isBeforeWriteIgnoreRemote() {
        return beforeWriteIgnoreRemote;
    }

    public void setBeforeWriteIgnoreRemote(boolean beforeWriteIgnoreRemote) {
        this.beforeWriteIgnoreRemote = beforeWriteIgnoreRemote;
    }
}
