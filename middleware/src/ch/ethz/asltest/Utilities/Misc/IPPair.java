package ch.ethz.asltest.Utilities.Misc;

public final class IPPair {
    public final String ip;
    public final int port;

    public IPPair(String ip, int port)
    {
        this.ip = ip;
        this.port = port;
    }

    public static IPPair getIPPair(String pair)
    {
        String[] components = pair.split(":");
        String ip = components[0];
        int port = Integer.parseInt(components[1]);
        return new IPPair(ip, port);
    }
}
