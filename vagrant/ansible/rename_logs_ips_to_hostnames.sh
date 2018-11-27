#!/bin/bash

declare -A host_ip_mapping

# Memtier machine IPs
host_ip_mapping[192.168.122.101]="Client1"
host_ip_mapping[192.168.122.102]="Client2"
host_ip_mapping[192.168.122.103]="Client3"

# Middleware machine IPs
host_ip_mapping[192.168.122.104]="Middleware1"
host_ip_mapping[192.168.122.105]="Middleware2"

# Memcached machine IPs
host_ip_mapping[192.168.122.106]="Server1"
host_ip_mapping[192.168.122.107]="Server2"
host_ip_mapping[192.168.122.108]="Server3"

for c in "${!host_ip_mapping[@]}"; do
    echo "Renaming logs with $c to ${host_ip_mapping[$c]}..."
    find ~/asl-logs-mickeyv -iname "$c*" -exec rename 's/'"$c"'/'"${host_ip_mapping[$c]}"'/g' '{}' \;
done
