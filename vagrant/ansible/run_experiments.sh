#!/bin/bash

# Because Ansible isn't capable of running anything further than Experiment 2
# nicely a bash script needs to be used...

# If by chance this could be ansibelised, feel free to send me (mickeyv at
# student dot ethz dot ch) an ansible-only version. I tried and failed but
# would be glad for an educational lesson on this.

subexperiment_21()
{
    echo "Subexperiment 2.1: One Server"
    HOSTS_FILE="inventory_task21.ini"

    base="2.1:"
    for is_read in true false; do
        if [[ "${is_read}" == true ]]; then
            str1="$base GET";
        else
            str1="$base SET";
        fi
        str2="$str1 0 MW threads,"
        for rep_count in 1 2 3; do
            str3="$str2 rep ${rep_count}/3,"
            for vc_count in 01 02 04 08 16 32; do

                echo "$str3 for ${vc_count} MT clients"
                if [[ "${is_read}" == true ]]; then
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                else
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                fi

                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_22()
{
    echo "Subexperiment 2.2: Two Servers"
    HOSTS_FILE="inventory_task22.ini"

    base="2.2:"
    for is_read in true false; do
        if [[ "${is_read}" == true ]]; then
            str1="$base GET";
        else
            str1="$base SET";
        fi
        str2="$str1 using 0 middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2 repetition ${rep_count}/3"
            for vc_count in 01 02 04 08 16 32; do

                echo "$str3 for ${vc_count} clients"
                if [[ "${is_read}" == true ]]; then
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                else
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                fi

                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_31()
{
    echo "Subxperiment 3.1: One Middlewares"
    HOSTS_FILE="inventory_task31.ini"

    base="3.1:"
    for is_read in true false; do
        if [[ "${is_read}" == true ]]; then
            str1="$base GET";
        else
            str1="$base SET";
        fi
        for thread_count in 08 16 32 64; do
            str2="$str1 using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2 repetition ${rep_count}/3"
                for vc_count in 01 02 04 08 16 32; do

                    echo "$str3 for ${vc_count} clients"
                    if [[ "${is_read}" == true ]]; then
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                    else
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                    fi

                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    sleep 1
                done
            done
        done
    done
}

subexperiment_32()
{
    echo "Subexperiment 3.2: Two Middlewares"
    HOSTS_FILE="inventory_task32.ini"

    base="3.2:"
    for is_read in true false; do
        if [[ "${is_read}" == true ]]; then
            str1="$base GET";
        else
            str1="$base SET";
        fi
        for thread_count in 08 16 32 64; do
            str2="$str1 using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2 repetition ${rep_count}/3"
                for vc_count in 01 02 04 08 16 32; do

                    echo "$str3 for ${vc_count} clients"
                    if [[ "${is_read}" == true ]]; then
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                    else
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                    fi

                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    sleep 1
                done
            done
        done
    done
}

subexperiment_40()
{
    echo "Subexperiment 4.0: Full System"
    HOSTS_FILE="inventory_task40.ini"

    base="4.0:"
    str1="$base SET";
    for thread_count in 08 16 32 64; do
        str2="$str1 using ${thread_count} middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2 repetition ${rep_count}/3"
            for vc_count in 01 02 04 08 16 32; do

                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_51()
{
    echo "Subexperiment 5.1: Sharded Case"
    HOSTS_FILE="inventory_task51.ini"

    base="5.1:"
    thread_count=0
    for key_size in 1 3 6 9; do
        str1="$base SHARDED GET (${key_size})"
        str2="$str1 using ${thread_count} middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2 repetition ${rep_count}/3"
            vc_count = 02
            # for vc_count in 01 02 04 08 16 32; do

                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SHARDED_${key_size} set_ratio=0 get_ratio=${key_size} multiget_count=${key_size}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            # done
        done
    done
}

subexperiment_52()
{
    echo "Subexperiment 5.2: Non-sharded Case"
    HOSTS_FILE="inventory_task52.ini"

    base="5.2:"
    thread_count=0
    for key_size in 1 3 6 9; do
        str1="$base MULTIGET (${key_size})"
        str2="$str1 using ${thread_count} middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2 repetition ${rep_count}/3"
            vc_count = 02
            # for vc_count in 01 02 04 08 16 32; do

                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=MULTIGET_${key_size} set_ratio=0 get_ratio=${key_size} multiget_count=${key_size}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            # done
        done
    done
}

subexperiment_60()
{
    echo "Subexperiment 6.0: 2K Analysis"
    HOSTS_FILE="inventory_task60.ini"

    base="6.0:"
    for is_read in true false; do

        str1=""
        if [[ "${is_read}" == true ]]; then
            str1="$base GET";
        else
            str1="$base SET";
        fi

        for thread_count in 08 32; do
            str2="$str1 using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2 repetition ${rep_count}/3"
                    for middleware_count in 1 2; do

                        if [[ $middleware_count -eq 1 ]]; then
                            HOSTS_FILE_STEM="inventory_task60_1"
                            str4="$str3 with 1 middleware"
                        else
                            HOSTS_FILE_STEM="inventory_task60_2"
                            str4="$str3 with 2 middlewares"
                        fi

                        for memcached_count in 1 3; do

                            if [[ $memcached_count -eq 1 ]]; then
                                HOSTS_FILE="${HOSTS_FILE_STEM}_1.ini"
                                str4="$str3 connected to 1 memcached server."
                            else
                                HOSTS_FILE="${HOSTS_FILE_STEM}_3.ini"
                                str4="$str3 connected to 3 memcached servers."
                            fi

                            echo $str4

                            if [[ "${is_read}" == true ]]; then
                                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} type=GET set_ratio=0 get_ratio=1"
                            else
                                LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} type=SET set_ratio=1 get_ratio=0"
                            fi

                            ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                            ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/start_memtier.yml -e "${LOOP_EXPERIMENT_VARS}"
                            ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                        sleep 1
                    done
                done
            done
        done
    done
}

experiment_2()
{
    echo "Experiment 2: Baseline without Middleware"

    subexperiment_21
    subexperiment_22

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_3()
{
    echo "Experiment 3: Baseline with Middleware"

    # subexperiment_31
    subexperiment_32

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_4()
{
    echo "Experiment 4: Throughput for Writes"

    subexperiment_40

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_5()
{
    echo "Experiment 5: Gets and Multi-gets"

    subexperiment_51
    subexperiment_52

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_6()
{
    echo "2K Analysis"

    subexperiment_60

    ansible-playbook -i hosts.ini ./playbooks/basic_blocks/cleanup-logs.yml
}

environment_setup()
{
    echo "Setting up environment"
    ansible-playbook -i hosts.ini ./playbooks/server/restart_memcached.yml
    ansible-playbook -i hosts.ini ./playbooks/middleware/update_jar.yml
    ansible-playbook -i hosts.ini ./playbooks/middleware/send_helper.yml
    ansible-playbook -i hosts.ini ./playbooks/client/send_helper.yml
    ansible-playbook -i hosts.ini ./playbooks/client/populate_memcaches.yml
}

environment_setup
# experiment_2
# experiment_3
# experiment_4
# experiment_5
# experiment_6
