import math
import re
import StdLib as sl
import path_helper as ph

from operator import itemgetter


class MemtierParser:

    def __init__(self, seconds=0, set_summary=None, get_summary=None, total_summary=None,
                 set_histogram_percentages=None, get_histogram_percentages=None,
                 set_histogram_counts=None, get_histogram_counts=None):
        self.seconds = seconds

        # Summary tuples
        self.set_summary = set_summary if set_summary is not None else {}
        self.get_summary = get_summary if set_summary is not None else {}
        self.total_summary = total_summary if total_summary is not None else {}

        # Histogram of query types based on percentages
        self.set_histogram_percentage = set_histogram_percentages if set_histogram_percentages is not None else {}
        self.get_histogram_percentage = get_histogram_percentages if get_histogram_percentages is not None else {}

        # Histogram of query types based on counts
        self.set_histogram_count = set_histogram_counts if set_histogram_counts is not None else {}
        self.get_histogram_count = get_histogram_counts if get_histogram_counts is not None else {}

    def __add__(self, other):
        new_seconds = self.seconds
        new_set_summary = MemtierParser.dictionary_keywise_add(self.set_summary, other.set_summary)
        new_get_summary = MemtierParser.dictionary_keywise_add(self.get_summary, other.get_summary)
        new_total_summary = MemtierParser.dictionary_keywise_add(self.total_summary, other.total_summary)
        new_set_histogram_percentages = MemtierParser.dictionary_keywise_add(self.set_histogram_percentage,
                                                                             other.set_histogram_percentage)
        new_get_histogram_percentages = MemtierParser.dictionary_keywise_add(self.get_histogram_percentage,
                                                                             other.get_histogram_percentage)
        new_set_histogram_counts = MemtierParser.dictionary_keywise_add(self.set_histogram_count,
                                                                        other.set_histogram_count)
        new_get_histogram_counts = MemtierParser.dictionary_keywise_add(self.get_histogram_count,
                                                                        other.get_histogram_count)
        return MemtierParser(new_seconds, new_set_summary, new_get_summary, new_total_summary,
                             new_set_histogram_percentages, new_get_histogram_percentages,
                             new_set_histogram_counts, new_get_histogram_counts)

    def __str__(self):
        return "Seconds: {}\n" \
               "SET Summary: {}\n" \
               "GET Summary: {}\n" \
               "Total Summary: {}\n" \
               "SET histogram (percentage):\n{}\n" \
               "GET histogram (percentage):\n{}\n" \
               "SET histogram (count):\n{}\n" \
               "SET histogram (count):\n{}".format(self.seconds, self.set_summary, self.get_summary,
                                                     self.total_summary,
                                                     self.set_histogram_percentage, self.get_histogram_percentage,
                                                     self.set_histogram_count, self.get_histogram_count)

    def get_as_list(self):
        return [self.seconds, self.set_summary, self.get_summary, self.total_summary,
                self.set_histogram_percentage, self.get_histogram_percentage,
                self.set_histogram_count, self.get_histogram_count]

    def get_as_dict(self):
        return {'Seconds': self.seconds,
                'SET_Summary': self.set_summary,
                'GET_Summary': self.get_summary,
                'Total_Summary': self.total_summary,
                'SET_Histogram_Percentage': self.set_histogram_percentage,
                'GET_Histogram_Percentage': self.get_histogram_percentage,
                'SET_Histogram_Count': self.set_histogram_count,
                'GET_Histogram_Count': self.get_histogram_count}

    def parse_file(self, base_path, base_filename, histograms):
        """
        Msin parsing method which sets the instance fields to parsed values
        :param base_path: Full path for file to parse.
        :param histograms: Also parse histograms
        :return: Nothing
        """

        if histograms:
            set_histogram_memtier = [(0, 0)]
            get_histogram_memtier = [(0, 0)]

            regex_seconds = r"^\d+\s+Seconds"

            regex_set_results = r"^Sets"
            regex_get_results = r"^Gets"
            regex_totals_results = r"^Totals"

            filename = base_path.joinpath(base_filename + '.stdout')

            regex_get_histogram_entry = r"^GET\s+"
            regex_set_histogram_entry = r"^SET\s+"

            with open(filename, "r") as file:
                for line in file:
                    if re.match(regex_seconds, line):
                        self.seconds = sl.StdLib.get_sane_int(line.split()[0])
                        continue
                    if re.match(regex_set_results, line):
                        self.set_summary = MemtierParser.parse_result_line(line)
                        continue
                    if re.match(regex_get_results, line):
                        self.get_summary = MemtierParser.parse_result_line(line)
                        continue
                    if re.match(regex_totals_results, line):
                        self.total_summary = MemtierParser.parse_result_line(line)
                        continue
                    if re.match(regex_get_histogram_entry, line):
                        get_histogram_memtier.append(MemtierParser.parse_histogram_entry(line))
                        continue
                    if re.match(regex_set_histogram_entry, line):
                        set_histogram_memtier.append(MemtierParser.parse_histogram_entry(line))
                        continue

            memtier_set_histogram_percentage = dict(MemtierParser.transform_from_cdf(set_histogram_memtier))
            memtier_get_histogram_percentage = dict(MemtierParser.transform_from_cdf(get_histogram_memtier))

            self.set_histogram_percentage = MemtierParser.transform_to_our_buckets(memtier_set_histogram_percentage)
            self.get_histogram_percentage = MemtierParser.transform_to_our_buckets(memtier_get_histogram_percentage)

            self.set_histogram_count = MemtierParser.percentages_to_counts(self.set_histogram_percentage,
                                                                           self.set_summary['Request_Throughput'] * self.seconds)
            self.get_histogram_count = MemtierParser.percentages_to_counts(self.get_histogram_percentage,
                                                                           self.get_summary['Request_Throughput'] * self.seconds)

        else:
            path_interpretation = ph.PathHelper.interpret_path(base_path)
            filename = base_path.joinpath(base_filename + '.stderr')
            memtier_history = []

            regex_history = r"\[RUN\s+#\d+\s+\d+%,\s+(\d+)\s+secs\]\s+\d+\s+threads:\s+\d+\s+ops,\s+(\d+)\s+\(avg:\s+\d+\)\s+ops/sec,\s+\d+\.\d+../sec\s+\(avg:\s+\d+\.\d+../sec\),\s+(\d+\.\d+)\s+\(avg:\s+\d+\.\d+\)\s+msec\s+latency"

            with open(filename, "r") as file:
                for line in file:
                    result = re.findall(regex_history, line)
                    for item in result:
                        memtier_history.append(item)

            memtier_history.sort(key=itemgetter(0))

            high_performance_section = []
            last_second = -1
            last_ops = 0
            last_latency = []
            for item in memtier_history:
                if int(item[0]) > 9 or int(item[0]) < 71: # 60 second range
                    if item[0] != last_second:
                        if last_second != -1:
                            high_performance_section.append((last_second - 10, last_ops, sum(last_latency)/len(last_latency)))
                            last_second = int(item[0])
                            last_ops = int(item[1])
                            last_latency.append(float(item[2]))
                        else:
                            last_second = int(item[0])
                            last_ops = int(item[1])
                            last_latency.append(float(item[2]))
                    else:
                        last_ops += int(item[1])
                        last_latency.append(float(item[2]))

            # Sanity check to have each second printed out


            average_latency = sum(tup[2] for tup in high_performance_section)/len(high_performance_section)
            average_throughput = sum(tup[1] for tup in high_performance_section)/len(high_performance_section)

            if path_interpretation['type'] == 'GET':
                self.get_summary = {'Request_Throughput': average_throughput, 'Hits': None, 'Misses': None,
                                    'Latency': average_latency, 'Data_Throughput': None}
            else:
                if path_interpretation['type'] == 'SET':
                    self.set_summary = {'Request_Throughput': average_throughput, 'Hits': None, 'Misses': None,
                                        'Latency': average_latency, 'Data_Throughput': None}
                else:
                    self.get_summary = {'Request_Throughput': average_throughput/2, 'Hits': None, 'Misses': None,
                                        'Latency': average_latency, 'Data_Throughput': None}
                    self.set_summary = {'Request_Throughput': average_throughput/2, 'Hits': None, 'Misses': None,
                                        'Latency': average_latency, 'Data_Throughput': None}


    @staticmethod
    def parse_result_line(line, prefix=''):
        """
        Function to parse a memtier summary line, returns any non-valid numbers with default value 0.0.
        :param prefix: Prefix to use for keys
        :param line: Line to parse (guaranteed by caller to be a valid summary line).
        :return: Parsed elements as tuples of doubles.
        """
        type, throughput, hits, misses, latency, data_throughput = line.split()

        throughput = sl.StdLib.get_sane_double(throughput)
        hits = sl.StdLib.get_sane_double(hits)
        misses = sl.StdLib.get_sane_double(misses)
        latency = sl.StdLib.get_sane_double(latency)
        data_throughput = sl.StdLib.get_sane_double(data_throughput)

        return {prefix+'Request_Throughput': throughput, prefix+'Hits:': hits, prefix+'Misses': misses,
                prefix+'Latency': latency, prefix+'Data_Throughput': data_throughput}

    @staticmethod
    def parse_histogram_entry(line):
        """
        Function to parse a memtier histogram line, returns a valid tuple.
        :param line: Line to parse (guaranteed by caller to be a valid histogram line).
        :return: Parsed elements as tuples of doubles.
        """
        type, bucket, cdf_until_and_including_bucket = line.split()

        bucket = sl.StdLib.get_sane_double(bucket)
        cdf_until_and_including_bucket = sl.StdLib.get_sane_double(cdf_until_and_including_bucket)

        return bucket, cdf_until_and_including_bucket

    @staticmethod
    def transform_from_cdf(memtier_histogram):
        """
        Function to transform the CDF returned by memtier into single bucket probabilities.
        :param memtier_histogram: Array of tuples of memtier histogram (must be ordered).
        :return: Array of tuples (timestamp_range, distribution)
        """
        return [(elem1[0], elem1[1] - elem0[1]) for elem0, elem1 in zip(memtier_histogram, memtier_histogram[1:])]

    @staticmethod
    def transform_to_our_buckets(percentages_memtier):
        """
        Returns for given memtier-based histogram a comparable histogram to our middleware's output.
        :param percentages_memtier: Histogram as returned per memtier in format
        :return: Histogram which has 100µs buckets between [0, 49.9] ms
        """
        result = {}

        expected_buckets = 200

        # Default initialize the result-dictionary to our needs
        for i in range(expected_buckets):
            result[sl.StdLib.get_rounded_double(i / 10)] = 0.0

        sum_below_range = 0
        sum_beyond_range = 0
        current_below = 0
        # Fill in the observed values to the dictionary
        for item in percentages_memtier:
            if item >= expected_buckets/10:
                # Accumulate anything above 50 milliseconds
                sum_beyond_range += percentages_memtier.get(item)
                continue
            if item <= 1:
                if int(item*10) - int(current_below*10) > 0.99:
                    if not current_below == 0.0:
                        index = sl.StdLib.get_rounded_double(int(current_below*10)/10)
                        result[index] = sum_below_range
                    sum_below_range = percentages_memtier.get(item)
                    current_below = item
                    if item == 1:
                        current_below = 0
                        sum_below_range = 0
                        index = sl.StdLib.get_rounded_double(item)
                        result[index] = percentages_memtier.get(item)
                else:
                    sum_below_range += percentages_memtier.get(item)
                continue
            # Try to expand buckets and divide respective percentages evenly -> uniform distribution inside buckets
            bucket_count = int(math.pow(10, math.ceil(math.log10(item + 0.1)) - 1))
            bucket_value = sl.StdLib.safe_div(percentages_memtier.get(item), bucket_count)
            for i in range(bucket_count):
                index = sl.StdLib.get_rounded_double(item + i / 10)
                result[index] = bucket_value

        last_bucket = sl.StdLib.get_rounded_double((expected_buckets - 1)/10)
        result[last_bucket] += sum_beyond_range

        return result

    @staticmethod
    def percentages_to_counts(percentage_histogram, op_count):
        """
        Function to transform an dictionary of "timestamp: tuple" with percentages to counts.
        :param percentage_histogram: Dictionary with percentages (non cumulative).
        :return: Dictionary of "timestamp: counts"
        """
        return {ts: percentage_histogram[ts] / 100 * op_count for ts in percentage_histogram}

    @staticmethod
    def pretty_print_list(formatted_histogram):
        """
        Get a list of doubles to two decimals after the comma for each element in the tuple.
        :param formatted_histogram:
        :return: Array of tuples (timestamp, distribution) up to two decimal places.
        """
        return ["(%.1f, %.2f)" % (timestamp, distribution) for (timestamp, distribution) in formatted_histogram]

    @staticmethod
    def tuple_elementwise_add(tuple1, tuple2):
        return tuple(map(lambda x, y: x + y, tuple1, tuple2))

    @staticmethod
    def dictionary_keywise_add(dict1, dict2):
        result = {key: dict1.get(key) + dict2.get(key) for key in set(dict1) if dict1.get(key) is not None}
        for key in set(dict1):
            if dict1.get(key) is None:
                result[key] = None

        if 'Latency' in set(dict1) and 'Request_Throughput' in set(dict1):
            if dict1['Request_Throughput'] is None or dict1['Latency'] is None:
                result['Latency'] = None
            else:
                result['Latency'] = sl.StdLib.safe_div(
                    ((dict1['Request_Throughput'] * dict1['Latency']) + (dict2['Request_Throughput'] * dict2['Latency'])),
                    result['Request_Throughput'])
        return result