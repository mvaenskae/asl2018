package ch.ethz.asltest.Utilities.Packets.WorkUnit;

public enum WorkUnitType {
    INVALID {
        public String toString()
        {
            return "INVALID";
        }
    }, SET {
        public String toString()
        {
            return "set";
        }
    }, STORED {
        public String toString()
        {
            return "STORED";
        }
    }, GET {
        public String toString()
        {
            return "get";
        }
    }, VALUE {
        public String toString()
        {
            return "VALUE";
        }
    }, ERROR {
        public String toString()
        {
            return "ERROR";
        }
    }, SERVER_ERROR {
        public String toString()
        {
            return "SERVER_ERROR";
        }
    }, CLIENT_ERROR {
        public String toString()
        {
            return "CLIENT_ERROR";
        }
    }, END {
        public String toString()
        {
            return "END";
        }
    }
}
