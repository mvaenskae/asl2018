package ch.ethz.asltest.Utilities.Misc;

public final class Tuple<T, T1> {
    public final T first;
    public final T1 second;

    public Tuple(T t, T1 t1)
    {
        this.first = t;
        this.second = t1;
    }
}
