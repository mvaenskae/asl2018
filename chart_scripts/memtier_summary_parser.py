import os
import re
import sys
from collections import OrderedDict

def main(argc, argv):
    # Second argv is filename
    if argc > 3 or argc < 2:
        sys.exit("Invalid number of arguments. Expecting 2")

    set_results = {}
    get_results = {}
    totals_results = {}

    set_histogram_memtier = [(0, 0)]
    get_histogram_memtier = [(0, 0)]

    regex_set_results = r"^Sets"
    regex_get_results = r"^Gets"
    regex_totals_results = r"^Totals"

    regex_get_histogram_entry = r"^GET\s+"
    regex_set_histogram_entry = r"^SET\s+"

    with open(argv[1], "r") as file:
        for line in file:
            if re.match(regex_set_results, line):
                set_results = parse_result_line(line)
                continue
            if re.match(regex_get_results, line):
                get_results = parse_result_line(line)
                continue
            if re.match(regex_totals_results, line):
                totals_results = parse_result_line(line)
                continue
            if re.match(regex_get_histogram_entry, line):
                get_histogram_memtier.append(parse_histogram_entry(line))
                continue
            if re.match(regex_set_histogram_entry, line):
                set_histogram_memtier.append(parse_histogram_entry(line))
                continue

    set_histogram = transform_from_cdf(set_histogram_memtier)
    get_histogram = transform_from_cdf(get_histogram_memtier)

    print(set_results)
    print(get_results)
    print(totals_results)

    print(set_histogram_memtier)
    print(get_histogram_memtier)

    set_histogram_formatted = pretty_print_list(set_histogram)
    get_histogram_formatted = pretty_print_list(get_histogram)
    print(set_histogram_formatted)
    print(get_histogram_formatted)

def parse_result_line(line):
    type, throughput, hits, misses, latency, data_throughput = line.split()

    throughput = get_sane_double(throughput)
    hits = get_sane_double(hits)
    misses = get_sane_double(misses)
    latency = get_sane_double(latency)
    data_throughput = get_sane_double(data_throughput)

    return throughput, hits, misses, latency, data_throughput

def parse_histogram_entry(line):
    type, bucket, cdf_until_and_including_bucket = line.split()

    bucket = get_sane_double(bucket)
    cdf_until_and_including_bucket = get_sane_double(cdf_until_and_including_bucket)

    return bucket, cdf_until_and_including_bucket

def transform_from_cdf(memtier_histogram):
    return [(elem1[0], elem1[1] - elem0[1]) for elem0, elem1 in zip(memtier_histogram, memtier_histogram[1:])]

def pretty_print_list(formatted_histogram):
    return ["(%.2f, %.2f)" % (timestamp, distribution) for (timestamp, distribution) in formatted_histogram]

def get_sane_double(s):
    try:
        float(s)
        return float(s)
    except ValueError:
        return 0.0


if __name__ == '__main__':
    main(len(sys.argv), sys.argv)