package ch.ethz.asltest.Utilities;

public class Timestamps {

    private long arrivedOnSocket;
    private long putOnQueue;
    private long gotFromQueue;
    private long queryToMemcached;
    private long replyFromMemcached;

    public Timestamps(long ts) {
        arrivedOnSocket = ts;
    }

    public long getArrivedOnSocket() {
        return arrivedOnSocket;
    }

    public void setArrivedOnSocket(long arrivedOnSocket) {
        this.arrivedOnSocket = arrivedOnSocket;
    }

    public long getPutOnQueue() {
        return putOnQueue;
    }

    public void setPutOnQueue(long putOnQueue) {
        this.putOnQueue = putOnQueue;
    }

    public long getGotFromQueue() {
        return gotFromQueue;
    }

    public void setGotFromQueue(long gotFromQueue) {
        this.gotFromQueue = gotFromQueue;
    }

    public long getQueryToMemcached() {
        return queryToMemcached;
    }

    public void setQueryToMemcached(long queryToMemcached) {
        this.queryToMemcached = queryToMemcached;
    }

    public long getReplyFromMemcached() {
        return replyFromMemcached;
    }

    public void setReplyFromMemcached(long replyFromMemcached) {
        this.replyFromMemcached = replyFromMemcached;
    }
}