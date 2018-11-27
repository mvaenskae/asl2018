#!/bin/bash

declare -A host_ip_mapping

# Memtier machine IPs
host_ip_mapping[192.168.122.101]=10.0.0.1
host_ip_mapping[192.168.122.102]=10.0.0.2
host_ip_mapping[192.168.122.103]=10.0.0.3

# Middleware machine IPs
host_ip_mapping[192.168.122.104]=10.0.0.4
host_ip_mapping[192.168.122.105]=10.0.0.5

# Memcached machine IPs
host_ip_mapping[192.168.122.106]=10.0.0.6
host_ip_mapping[192.168.122.107]=10.0.0.7
host_ip_mapping[192.168.122.108]=10.0.0.8

for c in "${!host_ip_mapping[@]}"; do
    echo "Changing active IP from $c to ${host_ip_mapping[$c]} in inventory...."
    for i in inventory/*.ini; do
        sed -i 's/'"$c"'/'"${host_ip_mapping[$c]}"'/g' $i
    done
    echo "Also adjusting log renaming helper..."
    sed -i 's/'"$c"'/'"${host_ip_mapping[$c]}"'/g' ./rename_logs_ips_to_hostnames.sh
done
