package ch.ethz.asltest.Utilities.Misc;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StackTraceString {

    private final StringWriter sw = new StringWriter();
    private final PrintWriter pw = new PrintWriter(sw);

    public String toString(Exception e)
    {
        e.printStackTrace(this.pw);
        String result = this.sw.toString();
        this.sw.flush();
        this.pw.flush();
        return result;
    }
}
