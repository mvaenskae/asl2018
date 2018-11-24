#!/bin/bash

# Helper script to easily start memtier instances based on arguments which
# instantiate as many memtier instances as the list contains as parameters.

SERVER_PORT_PAIR=()
while [[ $# -gt 0 ]]
do
key="$1"

INSTRUMENTATION_PIDS=()
MEMTIER_PIDS=()

case $key in
    -k|--key-size)
    KEY_SIZE="$2"
    shift # past argument
    shift # past value
    ;;
    -d|--data-size)
    DATA_SIZE="$2"
    shift # past argument
    shift # past value
    ;;
    -c|--virtual-clients)
    VIRTUAL_CLIENTS="$2"
    shift # past argument
    shift # past value
    ;;
    -t|--threads)
    THREADS="$2"
    shift # past argument
    shift # past value
    ;;
    -s|--set-ratio)
    SET_RATIO="$2"
    shift # past argument
    shift # past value
    ;;
    -g|--get-ratio)
    GET_RATIO="$2"
    shift # past argument
    shift # past value
    ;;
    -m|--multiget-count)
    MULTIGET_COUNT="$2"
    USE_MULTIGET="true"
    shift # past argument
    shift # past value
    ;;
    -r|--runtime)
    RUNTIME="$2"
    shift # past argument
    shift # past value
    ;;
    -l|--log-path)
    LOG_PATH="$2"
    shift # past argument
    shift # past value
    ;;
    -p|--populate)
    POPULATE_ONLY="true"
    shift # past argument
    ;;
    *)    # List of servers
    SERVER_PORT_PAIR+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done

BASE_CMD="memtier_benchmark --protocol=memcache_text --expiry-range=$((60 * 60 * 24 * 3))-$((60 * 60 * 24 * 3 + 1)) --key-maximum=$KEY_SIZE --data-size=$DATA_SIZE --clients=$VIRTUAL_CLIENTS --threads=$THREADS"

memtier_cmd()
{
    if [[ "$USE_MULTIGET" == true && $MULTIGET_COUNT -gt 0 ]]; then
        MEMTIER_CMD="$BASE_CMD --test-time=$RUNTIME --ratio=$SET_RATIO:$MULTIGET_COUNT --multi-key-get=$MULTIGET_COUNT"
    else
        MEMTIER_CMD="$BASE_CMD --test-time=$RUNTIME --ratio=$SET_RATIO:$GET_RATIO"
    fi

    for server in ${SERVER_PORT_PAIR[*]}; do
        REMOTE=$( echo "$server" | cut -f1 -d: )
        PORT=$( echo "$server" | cut -f2 -d: )
        LOGNAME="${LOG_PATH}/${REMOTE}_${PORT}"
        echo "Memtier talking with $REMOTE:$PORT"
        $MEMTIER_CMD --server=$REMOTE --port=$PORT > "${LOGNAME}.stdout" 2> "${LOGNAME}.stderr" &
        MEMTIER_PIDS+=($!)
    done
}

instrumentation_cmd()
{
    echo "Starting dstat"
    dstat -tcpi --ipc -ylmsd --fs -n --socket --tcp > "${LOG_PATH}/trace.dstat" &
    INSTRUMENTATION_PIDS+=($!)

    for server in ${SERVER_PORT_PAIR[*]}; do
        REMOTE=$( echo "$server" | cut -f1 -d: )
        LOGNAME="${LOG_PATH}/${REMOTE}.ping"
        echo "Pinging $REMOTE"
        ping -i 1 $REMOTE > "${LOGNAME}" &
        INSTRUMENTATION_PIDS+=($!)
    done
}

fix_history()
{
    for server in ${SERVER_PORT_PAIR[*]}; do
        REMOTE=$( echo "$server" | cut -f1 -d: )
        PORT=$( echo "$server" | cut -f2 -d: )
        perl -p -i -e 's/\r/\n/g' "${LOG_PATH}/${REMOTE}_${PORT}".stderr
    done
}

populate()
{
    echo "Populating memcached servers ${SERVER_PORT_PAIR[*]}"
    MEMTIER_CMD="$BASE_CMD --requests=$(( $KEY_SIZE + 1 )) --ratio=1:0 --key-pattern=S:S"

    for server in ${SERVER_PORT_PAIR[*]}; do
        REMOTE=$( echo "$server" | cut -f1 -d: )
        PORT=$( echo "$server" | cut -f2 -d: )
        $MEMTIER_CMD --server=$REMOTE --port=$PORT &
        MEMTIER_PIDS+=($!)
    done

    echo "Waiting for population to finish..."
    for pid in ${MEMTIER_PIDS[*]}; do
        wait $pid
    done
}

main()
{
    if [[ "$POPULATE_ONLY" == true ]]; then
        populate
        return 0
    fi

    mkdir -p "${LOG_PATH}"
    instrumentation_cmd
    memtier_cmd

    echo "Waiting for memtier processes to finish"
    for pid in ${MEMTIER_PIDS[*]}; do
        wait $pid
    done

    echo "Killing instrumentation facilities as memtier finished"
    for pid in ${INSTRUMENTATION_PIDS[*]}; do
        kill $pid
    done

    fix_history

    echo "Goodbye :)"
}

main
