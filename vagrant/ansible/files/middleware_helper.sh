#!/bin/bash

# Helper script to easily start middleware instances based on arguments which
# instantiate as many middleware instances as the list contains as parameters.

SERVER_PORT_PAIR=()
while [[ $# -gt 0 ]]
do
key="$1"

INSTRUMENTATION_PIDS=()
MIDDLEWARE_PID=""

case $key in
    -j|-jar-path)
    JAR_PATH="$2"
    shift # past argument
    shift # past value
    ;;
    -i|--listen-ip)
    LISTEN_IP="$2"
    shift # past argument
    shift # past value
    ;;
    -p|--listen-port)
    LISTEN_PORT="$2"
    shift # past argument
    shift # past value
    ;;
    -t|--worker-threads)
    WORKER_THREADS="$2"
    shift # past argument
    shift # past value
    ;;
    -s|--is-sharded)
    IS_SHARDED="$2"
    shift # past argument
    shift # past value
    ;;
    -l|--log-path)
    LOG_PATH="$2"
    shift # past argument
    shift # past value
    ;;
    *)    # List of servers
    SERVER_PORT_PAIR+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done

middleware_cmd()
{
    MIDDLEWARE_CMD="java -jar ${JAR_PATH} -l ${LISTEN_IP} -p ${LISTEN_PORT} -t ${WORKER_THREADS} -s ${IS_SHARDED} -m ${SERVER_PORT_PAIR[*]}"

    echo "Middleware listening on ${LISTEN_IP}:${LISTEN_PORT} with memcached backends ${SERVER_PORT_PAIR[*]}"
    ${MIDDLEWARE_CMD} &
    MDDLEWARE_PID=$!
}

instrumentation_cmd()
{
    echo "Starting dstat"
    dstat > "${LOG_PATH}/trace.dstat" &
    INSTRUMENTATION_PIDS+=($!)

    for server in ${SERVER_PORT_PAIR[*]}; do
        REMOTE=$( echo "$server" | cut -f1 -d: )
        LOGNAME="${LOG_PATH}/${REMOTE}.ping"
        echo "Pinging $REMOTE"
        ping -i 1 $REMOTE > "${LOGNAME}" &
        INSTRUMENTATION_PIDS+=($!)
    done
}

main()
{
    mkdir -p "${LOG_PATH}"
    instrumentation_cmd
    middleware_cmd

    echo "Waiting for middleware processes to finish"
    wait $MIDDLEWARE_PID

    echo "Killing logging facilities as middleware finished"
    for pid in ${INSTRUMENTATION_PIDS[*]}; do
        kill $pid
    done

    echo "Moving middleware logs to final directory"
    mv ~/mw-stats "${LOG_PATH}"

    echo "Goodbye :)"
}

main
