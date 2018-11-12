package ch.ethz.asltest.Utilities.Statistics.Containers;

public final class WorkerStatistics extends MiddlewareStatistics {

    /*
     * This class manages local statistics for each worker. It tracks the waiting time for each element popped from the
     * queue (finalized it), a count of elements received from the queue and the service time for each memcached server.
     */

/*    private final static int RESOLUTION_PER_SECOND = 10_000; // 100µs resolution
    private final int NANOS_TO_BUCKET = 100_000; // 100µs resolution

    private long invalidPacketCounter;
    private long memcachedMisses;

    // Each bucket is in the range of [index - 1 * 100µs, index * 100µs) for index > 1.
    public long[] histogramCounterSet = new long[RESOLUTION_PER_SECOND];
    public long[] histogramCounterGet = new long[RESOLUTION_PER_SECOND];
    public long[] histogramCounterMultiGet = new long[RESOLUTION_PER_SECOND];

    // Per window variables which will be reset for each new window
    private int setCounter;
    private int getCounter;
    private int multiGetCounter;

    // Per window history of counters
    public final ArrayList<TimestampedElement> avgSetCount = new ArrayList<>();
    public final ArrayList<TimestampedElement> avgGetCount = new ArrayList<>();
    public final ArrayList<TimestampedElement> avgMultiGetCount = new ArrayList<>();
    public final ArrayList<TimestampedElement> avgQueriesProcessed = new ArrayList<>();

    // Counters which keep track of the total count per request
    public long totalSetCount;
    public long totalGetCount;
    public long totalMultiGetCount;

    private final HashMap<SelectionKey, ArrayList<ArrayList<Long>>> avgMemcachedResponseTimesSet = new HashMap<>();
    private final HashMap<SelectionKey, ArrayList<ArrayList<Long>>> avgMemcachedResponseTimesGet = new HashMap<>();
    private final HashMap<SelectionKey, ArrayList<ArrayList<Long>>> avgMemcachedResponseTimesMultiGet = new HashMap<>();

    private final ArrayList<Long> queueWaitingTimeSet = new ArrayList<>();
    private final ArrayList<Long> queueWaitingTimeGet = new ArrayList<>();
    private final ArrayList<Long> queueWaitingTimeMultiGet = new ArrayList<>();

    private final ArrayList<TimestampedElement> avgQueueWaitingTimeSet = new ArrayList<>();
    private final ArrayList<TimestampedElement> avgQueueWaitingTimeGet = new ArrayList<>();
    private final ArrayList<TimestampedElement> avgQueueWaitingTimeMultiGet = new ArrayList<>();

    private final ArrayList<Long> totalTimeSet = new ArrayList<>();
    private final ArrayList<Long> totalTimeGet = new ArrayList<>();
    private final ArrayList<Long> totalTimeMultiGet = new ArrayList<>();

    private final ArrayList<TimestampedElement> avgTotalTimeSet = new ArrayList<>();
    private final ArrayList<TimestampedElement> avgTotalTimeGet = new ArrayList<>();
    private final ArrayList<TimestampedElement> avgTotalTimeMultiGet = new ArrayList<>();



    private void putIntoHistogram(long inNanos, long[] histogramCounter)
    {
        long bucket = inNanos / NANOS_TO_BUCKET;
        bucket = (bucket > histogramCounter.length) ? histogramCounter.length : bucket;
        histogramCounter[(int) bucket]++;
    }

    public synchronized void insertWorkerElement(WorkerElement workerElement)
    {
        switch (workerElement.elementType) {
            case SET:
                setCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterSet);
                queueWaitingTimeSet.add(workerElement.queueWaitingTime);
                totalTimeSet.add(workerElement.totalResponseTime);
                break;
            case GET:
                getCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterGet);
                queueWaitingTimeGet.add(workerElement.queueWaitingTime);
                totalTimeGet.add(workerElement.totalResponseTime);
                break;
            case MULTIGET:
                multiGetCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterMultiGet);
                queueWaitingTimeMultiGet.add(workerElement.queueWaitingTime);
                totalTimeMultiGet.add(workerElement.totalResponseTime);
                break;
        }
    }

    public void cacheMiss(int i)
    {
        this.memcachedMisses += i;
    }

    public void cacheMiss()
    {
        this.memcachedMisses++;
    }

    public void invalidPacketCounter()
    {
        this.invalidPacketCounter++;
    }

    @Override
    public synchronized void accumulate()
    {
        if (this.enabled) {
            totalSetCount += setCounter;
            totalGetCount += getCounter;
            totalMultiGetCount += multiGetCounter;

            avgSetCount.add(new TimestampedElement<>(System.nanoTime(), setCounter));
            avgGetCount.add(new TimestampedElement<>(System.nanoTime(), getCounter));
            avgMultiGetCount.add(new TimestampedElement<>(System.nanoTime(), multiGetCounter));
            avgQueriesProcessed.add(new TimestampedElement<>(System.nanoTime(), setCounter + getCounter + multiGetCounter));

            setCounter = getCounter = multiGetCounter = 0;

            sumUpAndClear(avgQueueWaitingTimeSet, queueWaitingTimeSet);
            sumUpAndClear(avgQueueWaitingTimeGet, queueWaitingTimeGet);
            sumUpAndClear(avgQueueWaitingTimeMultiGet, queueWaitingTimeMultiGet);

            sumUpAndClear(avgTotalTimeSet, totalTimeSet);
            sumUpAndClear(avgTotalTimeGet, totalTimeGet);
            sumUpAndClear(avgTotalTimeMultiGet, totalTimeMultiGet);
        }
    }



    private void sumUpAndClear(ArrayList<Long> averageList, ArrayList<Long> windowList)
    {
        averageList.add(windowList.stream().mapToLong(Long::longValue).sum());
        windowList.clear();
    }

    public void add(WorkerStatistics other)
    {
        this.totalSetCount += other.totalSetCount;
        this.totalGetCount += other.totalGetCount;
        this.totalMultiGetCount += other.totalMultiGetCount;

        this.invalidPacketCounter += other.invalidPacketCounter;
        this.memcachedMisses += other.memcachedMisses;

        joinIntegerList(this.avgSetCount, other.avgSetCount);
        joinIntegerList(this.avgGetCount, other.avgGetCount);
        joinIntegerList(this.avgMultiGetCount, other.avgMultiGetCount);

        joinLongList(this.avgQueueWaitingTimeSet, other.avgQueueWaitingTimeSet);
        joinLongList(this.avgQueueWaitingTimeGet, other.avgQueueWaitingTimeGet);
        joinLongList(this.avgQueueWaitingTimeMultiGet, other.avgQueueWaitingTimeMultiGet);

        joinLongList(this.avgTotalTimeSet, other.avgTotalTimeSet);
        joinLongList(this.avgTotalTimeGet, other.avgTotalTimeGet);
        joinLongList(this.avgTotalTimeMultiGet, other.avgTotalTimeMultiGet);

        joinHistorgram(this.histogramCounterSet, other.histogramCounterSet);
        joinHistorgram(this.histogramCounterGet, other.histogramCounterGet);
        joinHistorgram(this.histogramCounterMultiGet, other.histogramCounterMultiGet);
    }

    public void normalizeTimes(int factor)
    {

    }

    private void joinHistorgram(long[] ours, long[] theirs)
    {
        for (int i = 0; i < theirs.length; i++) {
            ours[i] += theirs[i];
        }
    }

    private void joinLongList(ArrayList<Long> ours, ArrayList<Long> theirs)
    {
        for (int i = 0; i < theirs.size(); ++i) {
            if (ours.size() < i) {
                ours.add(theirs.get(i));
            } else {
                ours.set(i, ours.get(i) + theirs.get(i));
            }
        }
    }

    private void joinIntegerList(ArrayList<Integer> ours, ArrayList<Integer> theirs)
    {
        for (int i = 0; i < theirs.size(); ++i) {
            if (ours.size() < i) {
                ours.add(theirs.get(i));
            } else {
                ours.set(i, ours.get(i) + theirs.get(i));
            }
        }
    }*/
}
