#!/bin/bash

# Because Ansible isn't capable of running anything further than Experiment 2
# nicely a bash script needs to be used...

# If by chance this could be ansibelised, feel free to send me (mickeyv at
# student dot ethz dot ch) an ansible-only version. I tried and failed but
# would be glad for an educational lesson on this.

MIDDLEWARE_PATH='~/middleware-mickeyv.jar'

subexperiment_21()
{
    echo "Subexperiment 2.1: One Server"
    HOSTS_FILE="inventory_task21.ini"

    base="2.1:"
    for is_read in true false; do
        str1=""
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
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/single_target.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_22()
{
    echo "Subexperiment 2.2: Two Servers"
    HOSTS_FILE="inventory_task22.ini"

    base="2.2: "
    for is_read in true false; do
        str1=""
        if [[ "${is_read}" == true ]]; then
            str1="$base""GET";
        else
            str1="$base""SET";
        fi
        str2="$str1"" using 0 middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2"" repetition ${rep_count}/3"
            for vc_count in 01 02 04 08 16 32; do
                echo "$str3 for ${vc_count} clients"
                if [[ "${is_read}" == true ]]; then
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                else
                    LOOP_EXPERIMENT_VARS="repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                fi
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/double_target.yml -e "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_31()
{
    echo "Subxperiment 3.1: One Middlewares"
    HOSTS_FILE="inventory_task31.ini"

    base="3.1: "
    for is_read in true false; do
        str1=""
        if [[ "${is_read}" == true ]]; then
            str1="$base""GET";
        else
            str1="$base""SET";
        fi
        for thread_count in 08 16 32 64; do
            str2="$str1"" using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2"" repetition ${rep_count}/3"
                for vc_count in 01 02 04 08 16 32; do
                    echo "$str3 for ${vc_count} clients"
                    if [[ "${is_read}" == true ]]; then
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                    else
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                    fi
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/single_target.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware_and_move_logs.yml -e "${LOOP_EXPERIMENT_VARS}"
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

    base="3.2: "
    for is_read in true false; do
        str1=""
        if [[ "${is_read}" == true ]]; then
            str1="$base""GET";
        else
            str1="$base""SET";
        fi
        for thread_count in 08 16 32 64; do
            str2="$str1"" using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2"" repetition ${rep_count}/3"
                for vc_count in 01 02 04 08 16 32; do
                    echo "$str3 for ${vc_count} clients"
                    if [[ "${is_read}" == true ]]; then
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=GET set_ratio=0 get_ratio=1"
                    else
                        LOOP_EXPERIMENT_VARS="worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count} type=SET set_ratio=1 get_ratio=0"
                    fi
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/double_target.yml -e "${LOOP_EXPERIMENT_VARS}"
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/stop_middleware_and_move_logs.yml -e "${LOOP_EXPERIMENT_VARS}"
                    sleep 1
                done
            done
        done
    done
}

subexperiment_41()
{
    echo "Subexperiment 4.1: Full System"
    HOSTS_FILE="inventory_task41.ini"

    base="4.1: "
    for is_read in true false; do
        str1=""
        if [[ "${is_read}" == true ]]; then
            str1="$base""GET";
        else
            str1="$base""SET";
        fi
        for thread_count in 08 16 32 64; do
            str2="$str1"" using ${thread_count} middleware threads"
            for rep_count in 1 2 3; do
                str3="$str2"" repetition ${rep_count}/3"
                for vc_count in 01 02 04 08 16 32; do
                    echo "$str3 for ${vc_count} clients"
                    LOOP_EXPERIMENT_VARS="-e \"worker_threads=${thread_count} repetition=${rep_count} vc=${vc_count}\""
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml "${LOOP_EXPERIMENT_VARS}"
                    if [[ "${is_read}" == true ]]; then
                        ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/read_double.yml "${LOOP_EXPERIMENT_VARS}"
                    else
                        ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/get_double.yml "${LOOP_EXPERIMENT_VARS}"
                    fi
                    ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/move_logs.yml "${LOOP_EXPERIMENT_VARS}"
                    sleep 1
                done
            done
        done
    done
}

subexperiment_51()
{
    echo "Subexperiment 5.1: Sharded Case"
    HOSTS_FILE="inventory_task51.ini"

    base="5.1: "
    for key_size in 1 3 6 9; do
        str1="$base""SHARDED GET (${key_size})"
        str2="$str1 using X middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2"" repetition ${rep_count}/3"
            for vc_count in 01 02 04 08 16 32; do
                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="-e \"get_ratio=${key_size} repetition=${rep_count} vc=${vc_count}\""
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/get_double.yml "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/move_logs.yml "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_52()
{
    echo "Subexperiment 5.2: Non-sharded Case"
    HOSTS_FILE="inventory_task52.ini"

    base="5.2: "
    for key_size in 1 3 6 9; do
        str1="$base"" GET (${key_size})"
        str2="$str1 using X middleware threads"
        for rep_count in 1 2 3; do
            str3="$str2"" repetition ${rep_count}/3"
            for vc_count in 01 02 04 08 16 32; do
                echo "$str3 for ${vc_count} clients"
                LOOP_EXPERIMENT_VARS="-e \"get_ratio=${key_size} repetition=${rep_count} vc=${vc_count}\""
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/start_middleware.yml "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/client/get_double.yml "${LOOP_EXPERIMENT_VARS}"
                ansible-playbook -i "${HOSTS_FILE}" ./playbooks/middleware/move_logs.yml "${LOOP_EXPERIMENT_VARS}"
                sleep 1
            done
        done
    done
}

subexperiment_61()
{
    echo "Implement me"
}

subexperiment_62()
{
    echo "Implement me"
}


experiment_2()
{
    echo "Experiment 2: Baseline without Middleware"

    subexperiment_21
    subexperiment_22

    ansible-playbook -i hosts ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_3()
{
    echo "Experiment 3: Baseline with Middleware"

    # subexperiment_31
    subexperiment_32

    ansible-playbook -i hosts ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_4()
{
    echo "Experiment 4: Throughput for Writes"

    subexperiment_41

    ansible-playbook -i hosts ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_5()
{
    echo "Experiment 5: Gets and Multi-gets"

    subexperiment_51
    subexperiment_52

    ansible-playbook -i hosts ./playbooks/basic_blocks/cleanup-logs.yml
}

experiment_6()
{
    echo "2K Analysis"

    subexperiment_61
    subexperiment_62

    ansible-playbook -i hosts ./playbooks/basic_blocks/cleanup-logs.yml
}

environment_setup()
{
    echo "Setting up environment"
    ansible-playbook -i host ./playbooks/server/restart_memcached.yml
    ansible-playbook -i host ./playbooks/middleware/update_jar.yml
    ansible-playbook -i host ./playbooks/client/populate_memcaches.yml
}

# environment_setup
# experiment_2
experiment_3
# experiment_4
# experiment_5
# experiment_6
