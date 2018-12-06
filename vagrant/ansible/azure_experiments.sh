#!/bin/bash

function stresstest()
{
    echo "Stresstest"
    HOSTS_FILE="inventory/task-test.ini"

    base="0.0:"
    is_read = false
    if [[ "${is_read}" == true ]]; then
        str1="$base GET";
    else
        str1="$base SET";
    fi
    for thread_count in 08 16 32 64; do
        str2="$str1 using ${thread_count} middleware threads"
        for rep_count in 1 2; do
            str3="$str2 repetition ${rep_count}/3"
            for vc_count in 16 32 40 48 56; do
                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"

                printf "Current Time: " && date
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

function setup_for_experiments() # Setup the environment for upcoming experiments
{
    echo "Setting up environment"

    ansible-playbook -i hosts.ini ./playbooks/server/restart_memcached.yml
    ansible-playbook -i hosts.ini ./playbooks/middleware/update_jar.yml
    ansible-playbook -i hosts.ini ./playbooks/middleware/send_helper.yml
    ansible-playbook -i hosts.ini ./playbooks/client/send_helper.yml
    ansible-playbook -i hosts.ini ./playbooks/client/populate_memcaches.yml

    echo "Sleeping Some"
    sleep 2
}

function experiments_full() # Run experiments on already provisioned machines
{
    setup_for_experiments
    echo "Experiments Done!"
}

function verify_ranges()
{
    setup_for_experiments
    printf "Current Time: " && date
    stresstest
    printf "Current Time: " && date

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/fetch-and-delete-logs.yml
    printf "Current Time: " && date
}

function help() # Show a list of functions
{
    grep "^function" $0 | sed 's/^function\ /\t-\ /g'
}

# Check if the function exists (bash specific)
if declare -f "$1" > /dev/null
then
  # call arguments verbatim
  "$@"
else
  # Show a helpful error
  echo "'$1' is not a known function name" >&2
  echo "Available functions are:"
  help
  exit -1
fi
