package ch.ethz.asltest.Utilities.Packets;

public final class Timestamps {

    private long arrivedOnSocket;
    private long pushOnQueue;
    private long popFromQueue;
    private long queryToMemcached;
    private long replyFromMemcached;
    private long replyOnSocket;

    public Timestamps(long ts)
    {
        arrivedOnSocket = ts;
    }

    public long getArrivedOnSocket()
    {
        return arrivedOnSocket;
    }

    public void setArrivedOnSocket(long arrivedOnSocket)
    {
        this.arrivedOnSocket = arrivedOnSocket;
    }

    public long getPushOnQueue()
    {
        return pushOnQueue;
    }

    public void setPushOnQueue(long pushOnQueue)
    {
        this.pushOnQueue = pushOnQueue;
    }

    public long getPopFromQueue()
    {
        return popFromQueue;
    }

    public void setPopFromQueue(long popFromQueue)
    {
        this.popFromQueue = popFromQueue;
    }

    public long getQueryToMemcached()
    {
        return queryToMemcached;
    }

    public void setQueryToMemcached(long queryToMemcached)
    {
        this.queryToMemcached = queryToMemcached;
    }

    public long getReplyFromMemcached()
    {
        return replyFromMemcached;
    }

    public void setReplyFromMemcached(long replyFromMemcached)
    {
        this.replyFromMemcached = replyFromMemcached;
    }

    public long getReplyOnSocket()
    {
        return replyOnSocket;
    }

    public void setReplyOnSocket(long replyOnSocket)
    {
        this.replyOnSocket = replyOnSocket;
    }
}