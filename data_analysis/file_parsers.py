import csv
import math
import re
import subprocess
from itertools import islice

from standard_lib import StdLib
from path_helper import PathHelper
from operator import itemgetter


class Parser:
    def __init__(self, seconds=0, clients=0, set_observed=None, get_observed=None,
                 set_interactive=None, get_interactive=None,
                 set_histogram_percentages=None, get_histogram_percentages=None,
                 set_histogram_counts=None, get_histogram_counts=None):
    
        self.seconds = seconds
        self.clients = clients

        # Summary tuples
        self.set_observed = set_observed if set_observed is not None else {}
        self.get_observed = get_observed if set_observed is not None else {}
        self.set_interactive = set_interactive if set_interactive is not None else {'Request_Throughput': None,
                                                                                    'Response_Time': None}
        self.get_interactive = get_interactive if get_interactive is not None else {'Request_Throughput': None,
                                                                                    'Response_Time': None}

        # Histogram of query types based on percentages
        self.set_histogram_percentage = set_histogram_percentages if set_histogram_percentages is not None else {}
        self.get_histogram_percentage = get_histogram_percentages if get_histogram_percentages is not None else {}

        # Histogram of query types based on counts
        self.set_histogram_count = set_histogram_counts if set_histogram_counts is not None else {}
        self.get_histogram_count = get_histogram_counts if get_histogram_counts is not None else {}

    def __str__(self):
        return "Seconds: {}\n" \
               "Active Clients: {}\n" \
               "SET Observed: {}\n" \
               "GET Observed: {}\n" \
               "SET Interactive Law: {}\n" \
               "GET Interactive Law: {}\n" \
               "SET histogram (percentage):\n{}\n" \
               "GET histogram (percentage):\n{}\n" \
               "SET histogram (count):\n{}\n" \
               "GET histogram (count):\n{}".format(self.seconds, self.clients, self.set_observed, self.get_observed,
                                                   self.set_interactive, self.get_interactive,
                                                   self.set_histogram_percentage, self.get_histogram_percentage,
                                                   self.set_histogram_count, self.get_histogram_count)


    def __add__(self, other):
        new_seconds = self.seconds
        new_set_histogram_percentages = Parser.dictionary_keywise_add(self.set_histogram_percentage,
                                                                      other.set_histogram_percentage)
        new_get_histogram_percentages = Parser.dictionary_keywise_add(self.get_histogram_percentage,
                                                                      other.get_histogram_percentage)
        new_set_histogram_counts = Parser.dictionary_keywise_add(self.set_histogram_count,
                                                                 other.set_histogram_count)
        new_get_histogram_counts = Parser.dictionary_keywise_add(self.get_histogram_count,
                                                                 other.get_histogram_count)
        return Parser(new_seconds, 0, None, None, None, None,
                      new_set_histogram_percentages, new_get_histogram_percentages,
                      new_set_histogram_counts, new_get_histogram_counts)

    @staticmethod
    def dictionary_keywise_add(dict1, dict2):
        result = {key: dict1.get(key) + dict2.get(key) for key in set(dict1) if dict1.get(key) is not None}
        for key in set(dict1):
            if dict1.get(key) is None:
                result[key] = None

        return result

    @staticmethod
    def transform_to_fixed_size_buckets(percentages_histogram, expected_buckets=200):
        """
        Returns for given histogram to a normalized histogram in term of buckets.
        :param percentages_histogram: Histogram as returned per memtier in format
        :param expected_buckets: Number of buckets expected in the histogram
        :return: Histogram which has 100µs buckets between [0, expected_buckets/10 - 0.1] ms
        """
        result = {}

        # Default initialize the result-dictionary to our needs
        for i in range(expected_buckets):
            result[StdLib.get_rounded_double(i / 10)] = 0.0

        sum_below_range = 0
        sum_beyond_range = 0
        current_below = 0
        # Fill in the observed values to the dictionary
        for item in percentages_histogram:
            if item >= expected_buckets / 10:
                # Accumulate anything above X milliseconds
                sum_beyond_range += percentages_histogram.get(item)
                continue
            if item <= 1:
                if int(item * 10) - int(current_below * 10) > 0.99:
                    if not current_below == 0.0:
                        index = StdLib.get_rounded_double(int(current_below * 10) / 10)
                        result[index] = sum_below_range
                    sum_below_range = percentages_histogram.get(item)
                    current_below = item
                    if item == 1:
                        current_below = 0
                        sum_below_range = 0
                        index = StdLib.get_rounded_double(item)
                        result[index] = percentages_histogram.get(item)
                else:
                    sum_below_range += percentages_histogram.get(item)
                continue
            # Try to expand buckets and divide respective percentages evenly -> uniform distribution inside buckets
            bucket_count = int(math.pow(10, math.ceil(math.log10(item + 0.1)) - 1))
            bucket_value = StdLib.safe_div(percentages_histogram.get(item), bucket_count)
            for i in range(bucket_count):
                index = StdLib.get_rounded_double(item + i / 10)
                result[index] = bucket_value

        last_bucket = StdLib.get_rounded_double((expected_buckets - 1) / 10)
        result[last_bucket] += sum_beyond_range

        return result

    @staticmethod
    def percentages_to_counts(percentage_histogram, op_count):
        """
        Function to transform an dictionary of "timestamp: tuple" with percentages to counts.
        :param percentage_histogram: Dictionary with percentages (non cumulative).
        :param op_count: Total number of operations
        :return: Dictionary of "timestamp: counts"
        """
        return {ts: percentage_histogram[ts] / 100 * op_count for ts in percentage_histogram}

    @staticmethod
    def counts_to_percentages(count_histogram, op_count):
        return {ts: 100*count_histogram[ts]/op_count for ts in count_histogram}

    @staticmethod
    def interactive_law_check(observed, num_clients):
        if observed['Response_Time'] is None or observed['Request_Throughput'] is None:
            return {'Request_Throughput': None, 'Response_Time': None}
        else:
            return {'Request_Throughput': 1000*num_clients/observed['Response_Time'],
                    'Response_Time': 1000*num_clients/observed['Request_Throughput']}


class MemtierParser(Parser):

    def __init__(self, seconds=0, clients=0,
                 set_observed={'Request_Throughput': None, 'Response_Time': None},
                 get_observed={'Request_Throughput': None, 'Response_Time': None},
                 set_interactive=None, get_interactive=None,
                 set_histogram_percentages=None, get_histogram_percentages=None,
                 set_histogram_counts=None, get_histogram_counts=None):
        super().__init__(seconds=seconds, clients=clients,
                         set_observed=set_observed, get_observed=get_observed,
                         set_interactive=set_interactive, get_interactive=get_interactive,
                         set_histogram_percentages=set_histogram_percentages,
                         get_histogram_percentages=get_histogram_percentages,
                         set_histogram_counts=set_histogram_counts, get_histogram_counts=get_histogram_counts)

    def __add__(self, other):
        p = super().__add__(other)
        new_clients = self.clients + other.clients
        new_set_observed = MemtierParser.dictionary_keywise_add(self.set_observed, other.set_observed)
        new_get_observed = MemtierParser.dictionary_keywise_add(self.get_observed, other.get_observed)
        new_set_interactive = Parser.interactive_law_check(new_set_observed, new_clients)
        new_get_interactive = Parser.interactive_law_check(new_get_observed, new_clients)
        return MemtierParser(p.seconds, new_clients, new_set_observed, new_get_observed,
                             new_set_interactive, new_get_interactive,
                             p.set_histogram_percentage, p.get_histogram_percentage,
                             p.set_histogram_count, p.set_histogram_count)

    @staticmethod
    def dictionary_keywise_add(dict1, dict2):
        result = {key: dict1.get(key) + dict2.get(key) for key in set(dict1) if dict1.get(key) is not None}
        for key in set(dict1):
            if dict1.get(key) is None:
                result[key] = None

        if 'Request_Throughput' in set(dict1):
            keys_to_average = ['Response_Time']
            for key in keys_to_average:
                if key in set(dict1):
                    if dict1['Request_Throughput'] is None or dict1[key] is None:
                        result[key] = None
                    else:
                        result[key] = StdLib.safe_div(((dict1['Request_Throughput'] * dict1[key]) +
                                                       (dict2['Request_Throughput'] * dict2[key])),
                                                      result['Request_Throughput'])
        return result

    def get_as_dict(self):
        return {'Seconds': self.seconds,
                'Active_Clients': self.clients,
                'SET_Observed': self.set_observed,
                'GET_Observed': self.get_observed,
                'SET_Interactive': self.set_interactive,
                'GET_Interactive': self.get_interactive,
                'SET_Histogram_Percentage': self.set_histogram_percentage,
                'GET_Histogram_Percentage': self.get_histogram_percentage,
                'SET_Histogram_Count': self.set_histogram_count,
                'GET_Histogram_Count': self.get_histogram_count}

    def parse_file(self, base_path, base_filename, histograms):
        """
        Main parsing method which sets the instance fields to parsed values
        :param base_path: Base path to the memtier file to parse
        :param base_filename: Base filename to the memtier file to parse (without file ending)
        :param histograms: Parse histograms, implies the parsing of the summary only and not history!
        :return: Nothing
        """
        path_interpretation = PathHelper.interpret_path(base_path)
        self.clients = int(path_interpretation['vc']) * int(path_interpretation['ct'])
        if histograms:
            # Histograms include the full range, therefore use the summary as a shortcut
            set_histogram_memtier = [(0, 0)]
            get_histogram_memtier = [(0, 0)]

            regex_seconds = r"^\d+\s+Seconds"

            regex_set_results = r"^Sets"
            regex_get_results = r"^Gets"

            filename = base_path.joinpath(base_filename + '.stdout')

            regex_get_histogram_entry = r"^GET\s+"
            regex_set_histogram_entry = r"^SET\s+"

            with open(filename, "r") as file:
                for line in file:
                    if re.match(regex_seconds, line):
                        self.seconds = StdLib.get_sane_int(line.split()[0])
                        continue
                    if re.match(regex_set_results, line):
                        if path_interpretation['type'] != 'GET':
                            self.set_observed = MemtierParser.parse_result_line(line)
                        continue
                    if re.match(regex_get_results, line):
                        if path_interpretation['type'] != 'SET':
                            self.get_observed = MemtierParser.parse_result_line(line)
                        continue
                    if re.match(regex_get_histogram_entry, line):
                        get_histogram_memtier.append(MemtierParser.parse_histogram_entry(line))
                        continue
                    if re.match(regex_set_histogram_entry, line):
                        set_histogram_memtier.append(MemtierParser.parse_histogram_entry(line))
                        continue

            memtier_set_histogram_percentage = dict(MemtierParser.transform_from_cdf(set_histogram_memtier))
            memtier_get_histogram_percentage = dict(MemtierParser.transform_from_cdf(get_histogram_memtier))

            self.set_histogram_percentage = Parser.transform_to_fixed_size_buckets(
                memtier_set_histogram_percentage)
            self.get_histogram_percentage = Parser.transform_to_fixed_size_buckets(
                memtier_get_histogram_percentage)

            if self.set_observed['Request_Throughput'] is None:
                self.set_histogram_count = Parser.percentages_to_counts(self.set_histogram_percentage, 0)
            else:
                self.set_histogram_count = Parser.percentages_to_counts(self.set_histogram_percentage,
                                                                        self.set_observed['Request_Throughput'] *
                                                                        self.seconds)
            if self.get_observed['Request_Throughput'] is None:
                self.get_histogram_count = Parser.percentages_to_counts(self.get_histogram_percentage, 0)
            else:
                self.get_histogram_count = Parser.percentages_to_counts(self.get_histogram_percentage,
                                                                        self.get_observed['Request_Throughput'] *
                                                                        self.seconds)

        else:
            # Memtier history should be used to cut off the first 10 and consume the next consecutive 60 seconds
            filename = base_path.joinpath(base_filename + '.stderr')
            memtier_history = []

            regex_history = r"\[RUN\s+#\d+\s+\d+%,\s+(\d+)\s+secs\]\s+\d+\s+threads:\s+\d+\s+ops,\s+(\d+)\s+\(avg:\s+\d+\)\s+ops/sec,\s+\d+\.\d+../sec\s+\(avg:\s+\d+\.\d+../sec\),\s+(\d+\.\d+)\s+\(avg:\s+\d+\.\d+\)\s+msec\s+latency"

            # Read in all lines
            with open(filename, "r") as file:
                for line in file:
                    result = re.findall(regex_history, line)
                    for second, ops, latency in result:
                        memtier_history.append((int(second), int(ops), float(latency)))

            # Extract the actual 60 second window
            high_performance_section = MemtierParser.extract_stable_window(memtier_history)
            seconds = len(high_performance_section)

            # Calculate the averages from extracted ops and latencies
            _, average_throughput, average_response_time = map(lambda x: x / seconds, [sum(x) for x in
                                                                                       zip(*high_performance_section)])

            self.seconds = seconds
            # Store extracted values in the correct result type
            if path_interpretation['type'] == 'GET':
                self.get_observed = {'Request_Throughput': average_throughput, 'Hits': None, 'Misses': None,
                                     'Response_Time': average_response_time, 'Data_Throughput': None}
                t = MemtierParser.get_line_from_summary(base_path, base_filename)
                self.get_observed['Hits'] = t['Hits']
                self.get_observed['Misses'] = t['Misses']
            elif path_interpretation['type'] == 'SET':
                self.set_observed = {'Request_Throughput': average_throughput,
                                     'Response_Time': average_response_time, 'Data_Throughput': None}
            else:
                # Assumption: Latencies stay constant between GET and SET requests. This is incorrect as such this
                #             should not be used for mixed-type approaches!
                self.get_observed = {'Request_Throughput': average_throughput / 2, 'Hits': None, 'Misses': None,
                                     'Response_Time': average_response_time, 'Data_Throughput': None}
                self.set_observed = {'Request_Throughput': average_throughput / 2,
                                     'Response_Time': average_response_time, 'Data_Throughput': None}
                t = MemtierParser.get_line_from_summary(base_path, base_filename)
                self.get_observed['Hits'] = t['Hits']
                self.get_observed['Misses'] = t['Misses']

        self.set_interactive = Parser.interactive_law_check(self.set_observed, self.clients)
        self.get_interactive = Parser.interactive_law_check(self.get_observed, self.clients)
        
    @staticmethod
    def get_line_from_summary(base_path, base_filename):
        with open(base_path.joinpath(base_filename + '.stdout'), "r") as file:
            for line in file:
                if re.match(r"^Gets", line):
                    return MemtierParser.parse_result_line(line)

    @staticmethod
    def extract_stable_window(history_list):
        """
        Extracts the 60 second window after 10 seconds of experimental time have elapsed. This should give a stable view
        of the system at full usage.
        :param history_list: List of Tuples (Second, Ops, Response_Time) to operate on.
        :return: The window in question,
        """
        # Sort lines by second, required for extracting the 60 second window of high performance
        history_list.sort(key=itemgetter(0))
        stable_window = history_list[10:70]
        return stable_window

    @staticmethod
    def parse_result_line(line):
        """
        Function to parse a memtier summary line, returns any non-valid numbers with default value 0.0.
        :param line: Line to parse (guaranteed by caller to be a valid summary line).
        :return: Parsed elements as tuples of doubles.
        """
        r_type, throughput, hits, misses, response_time, data_throughput = line.split()

        throughput = StdLib.get_sane_double(throughput)
        response_time = StdLib.get_sane_double(response_time)
        data_throughput = StdLib.get_sane_double(data_throughput)
        if r_type == 'Gets':
            hits = StdLib.get_sane_double(hits)
            misses = StdLib.get_sane_double(misses)
            return {'Request_Throughput': throughput, 'Hits': hits, 'Misses': misses, 'Response_Time': response_time,
                    'Data_Throughput': data_throughput}
        else:
            return {'Request_Throughput': throughput, 'Response_Time': response_time,
                    'Data_Throughput': data_throughput}

    @staticmethod
    def parse_histogram_entry(line):
        """
        Function to parse a memtier histogram line, returns a valid tuple.
        :param line: Line to parse (guaranteed by caller to be a valid histogram line).
        :return: Parsed elements as tuples of doubles.
        """
        _, bucket, cdf_until_and_including_bucket = line.split()

        bucket = StdLib.get_sane_double(bucket)
        cdf_until_and_including_bucket = StdLib.get_sane_double(cdf_until_and_including_bucket)

        return bucket, cdf_until_and_including_bucket

    @staticmethod
    def transform_from_cdf(memtier_histogram):
        """
        Function to transform the CDF returned by memtier into single bucket probabilities.
        :param memtier_histogram: Array of tuples of memtier histogram (must be ordered).
        :return: Array of tuples (timestamp_range, distribution)
        """
        return [(elem1[0], elem1[1] - elem0[1]) for elem0, elem1 in zip(memtier_histogram, memtier_histogram[1:])]


class MiddlewareParser(Parser):

    def __init__(self, seconds=0, clients=0,
                 set_observed={'Request_Throughput': None, 'Response_Time': None},
                 get_observed={'Request_Throughput': None, 'Response_Time': None},
                 set_interactive=None, get_interactive=None,
                 set_histogram_percentages=None, get_histogram_percentages=None,
                 set_histogram_counts=None, get_histogram_counts=None):
        super().__init__(seconds=seconds, clients=clients,
                         set_observed=set_observed, get_observed=get_observed,
                         set_interactive=set_interactive, get_interactive=get_interactive,
                         set_histogram_percentages=set_histogram_percentages,
                         get_histogram_percentages=get_histogram_percentages,
                         set_histogram_counts=set_histogram_counts, get_histogram_counts=get_histogram_counts)

    def __add__(self, other):
        p = super().__add__(other)
        new_clients = self.clients + other.clients
        new_set_observed = MiddlewareParser.dictionary_keywise_add(self.set_observed, other.set_observed)
        new_get_observed = MiddlewareParser.dictionary_keywise_add(self.get_observed, other.get_observed)
        new_set_interactive = Parser.interactive_law_check(new_set_observed, new_clients)
        new_get_interactive = Parser.interactive_law_check(new_get_observed, new_clients)
        return MemtierParser(p.seconds, new_clients, new_set_observed, new_get_observed,
                             new_set_interactive, new_get_interactive,
                             p.set_histogram_percentage, p.get_histogram_percentage,
                             p.set_histogram_count, p.set_histogram_count)

    @staticmethod
    def dictionary_keywise_add(dict1, dict2):
        result = {key: dict1.get(key) + dict2.get(key) for key in set(dict1) if dict1.get(key) is not None}
        for key in set(dict1):
            if dict1.get(key) is None:
                result[key] = None

        if 'Request_Throughput' in set(dict1):
            keys_to_average = ['Response_Time', 'Queue_Size', 'RTT', 'Queue_Waiting_Time', 'Request_Size']
            for key in keys_to_average:
                if key in set(dict1):
                    if dict1['Request_Throughput'] is None or dict1[key] is None:
                        result[key] = None
                    else:
                        result[key] = StdLib.safe_div(((dict1['Request_Throughput'] * dict1[key]) +
                                                       (dict2['Request_Throughput'] * dict2[key])),
                                                      result['Request_Throughput'])
        return result

    def get_as_dict(self):
        return {'Seconds': self.seconds,
                'Active_Clients': self.clients,
                'SET_Observed': self.set_observed,
                'GET_Observed': self.get_observed,
                'SET_Interactive': self.set_interactive,
                'GET_Interactive': self.get_interactive,
                'SET_Histogram_Percentage': self.set_histogram_percentage,
                'GET_Histogram_Percentage': self.get_histogram_percentage,
                'SET_Histogram_Count': self.set_histogram_count,
                'GET_Histogram_Count': self.get_histogram_count}

    def parse_dir(self, path_to_dir, memtier_clients, histograms=False):
        path_information = PathHelper.interpret_path(path_to_dir)
        self.clients = memtier_clients * int(path_information['vc']) * int(path_information['ct'])

        if path_information['type'] in ('GET', 'MULTIGET_1', 'SHARDED_1'):
            self.parse_table_with_queue(path_to_dir, 'GET', histograms)
            if path_information['type'] != 'GET':
                self.parse_table_with_queue(path_to_dir, 'SET', histograms)
        elif path_information['type'] in ('MULTIGET_3', 'MULTIGET_6', 'MULTIGET_9', 'SHARDED_3', 'SHARDED_6', 'SHARDED_9'):
            self.parse_table_with_queue(path_to_dir, 'MULTIGET', histograms)
            self.parse_table_with_queue(path_to_dir, 'SET', histograms)
        else:
            self.parse_table_with_queue(path_to_dir, 'SET', histograms)

        if histograms:
            # We use the whole range found on the middleware
            if path_information['type'] in ('GET', 'MULTIGET_1', 'SHARDED_1'):
                self.parse_histogram(path_to_dir, 'GET')
                if path_information['type'] != 'GET':
                    self.parse_histogram(path_to_dir, 'SET')
            elif path_information['type'] in ('MULTIGET_3', 'MULTIGET_6', 'MULTIGET_9', 'SHARDED_3', 'SHARDED_6', 'SHARDED_9'):
                self.parse_histogram(path_to_dir, 'MULTIGET')
                self.parse_histogram(path_to_dir, 'SET')
            else:
                self.parse_histogram(path_to_dir, 'SET')

        self.set_interactive = Parser.interactive_law_check(self.set_observed, self.clients)
        self.get_interactive = Parser.interactive_law_check(self.get_observed, self.clients)

    def parse_table_with_queue(self, base_path, r_type, histogram):
        mw_path = base_path.joinpath('mw-stats')
        queue_file = mw_path.joinpath('queue_statistics.txt')
        if r_type == 'GET':
            table_csv = mw_path.joinpath('get_table.csv')
        elif r_type == 'SET':
            table_csv = mw_path.joinpath('set_table.csv')
        else:
            table_csv = mw_path.joinpath('multiget_table.csv')

        table_rows = MiddlewareParser.csv_parsing(table_csv, histogram, delimiter=',', has_header=True)
        self.seconds = len(table_rows)

        queue_rows = MiddlewareParser.csv_parsing(queue_file, histogram)
        _, queue_average = map(lambda x: x / self.seconds, [sum(x) for x in zip(*queue_rows)])

        if r_type == 'SET':
            self.parse_set_list(table_rows)
            self.set_observed['Queue_Size'] = queue_average
        elif r_type == 'GET':
            self.parse_get_list(table_rows)
            self.get_observed['Queue_Size'] = queue_average
        else:
            self.parse_multiget_list(table_rows)
            key_distribution = MiddlewareParser.tail(mw_path.joinpath('multiget_summary.txt'))
            self.get_observed['Queue_Size'] = queue_average
            self.get_observed['Key_Distribution'] = tuple(key_distribution.split())

    def parse_histogram(self, base_path, r_type):
        mw_path = base_path.joinpath('mw-stats')
        if r_type == 'GET':
            histogram_filepath = mw_path.joinpath('get_histogram.txt')
            total_ops = self.get_observed['Request_Throughput'] * self.seconds
        elif r_type == 'SET':
            histogram_filepath = mw_path.joinpath('set_histogram.txt')
            total_ops = self.set_observed['Request_Throughput'] * self.seconds
        else:
            histogram_filepath = mw_path.joinpath('multiget_histogram.txt')
            total_ops = self.get_observed['Request_Throughput'] * self.seconds

        table_rows = MiddlewareParser.csv_parsing(histogram_filepath, True)
        histogram_counts = MiddlewareParser.transform_to_fixed_size_buckets(dict(table_rows))
        histogram = Parser.counts_to_percentages(histogram_counts, total_ops)

        if r_type == 'SET':
            self.set_histogram_percentage = histogram
            self.set_histogram_count = histogram_counts
        else:
            self.get_histogram_percentage = histogram
            self.get_histogram_count = histogram_counts


    @staticmethod
    def csv_parsing(csv_file, histogram, delimiter=' ', has_header=False):
        rows = []
        with open(csv_file, newline='') as csvfile:
            walker = csv.reader(csvfile, delimiter=delimiter)
            if has_header:
                next(walker)
            if not histogram:
                for row in islice(walker, 10, 70):
                    rows.append(tuple(float(item) for item in row))
            else:
                for row in walker:
                    if float(row[1]) != 0.0:
                        rows.append(tuple(float(item) for item in row))
        return rows

    def parse_set_list(self, rows):
        _, throughput, queue_waiting_time, memcached_waiting_time, rtt = map(lambda x: x / self.seconds,
                                                                             [sum(x) for x in zip(*rows)])
        queue_waiting_time /= 1e6
        memcached_waiting_time /= 1e6
        rtt /= 1e6

        self.set_observed = {'Request_Throughput': throughput, 'Response_Time': rtt,
                             'Queue_Waiting_Time': queue_waiting_time, 'Memcached_Communication': memcached_waiting_time}

    def parse_get_list(self, rows):
        _, throughput, queue_waiting_time, memcached_waiting_time, rtt, misses = map(lambda x: x / self.seconds,
                                                                                     [sum(x) for x in zip(*rows)])

        queue_waiting_time /= 1e6
        memcached_waiting_time /= 1e6
        rtt /= 1e6
        hits = throughput - misses

        self.get_observed = {'Request_Throughput': throughput, 'Response_Time': rtt,
                             'Queue_Waiting_Time': queue_waiting_time, 'Memcached_Communication': memcached_waiting_time,
                             'Hits': hits, 'Misses': misses,
                             'Request_Size': 1, 'Key_Throughput': throughput}

    def parse_multiget_list(self, rows):
        _, throughput, queue_waiting_time, memcached_waiting_time, rtt, misses, keys_requested, keysize = \
            map(lambda x: x / self.seconds, [sum(x) for x in zip(*rows)])

        queue_waiting_time /= 1e6
        memcached_waiting_time /= 1e6
        rtt /= 1e6
        hits = keys_requested - misses

        self.get_observed = {'Request_Throughput': throughput, 'Response_Time': rtt,
                             'Queue_Waiting_Time': queue_waiting_time, 'Memcached_Communication': memcached_waiting_time,
                             'Hits': hits, 'Misses': misses,
                             'Request_Size': keysize, 'Key_Throughput': keys_requested}

    @staticmethod
    def tail(filename):
        proc = subprocess.Popen(['tail', '-n 1', filename], stdout=subprocess.PIPE)
        lines = proc.stdout.readlines()
        result = lines[0].decode('ascii')
        return result[:-2]

    @staticmethod
    def transform_to_fixed_size_buckets(percentages_histogram, expected_buckets=200):
        """
        Returns for given histogram to a normalized histogram in term of buckets.
        :param percentages_histogram: Histogram as returned per middleware in format
        :param expected_buckets: Number of buckets expected in the histogram
        :return: Histogram which has 100µs buckets between [0, expected_buckets/10 - 0.1] ms
        """
        result = {}

        # Default initialize the result-dictionary to our needs
        for i in range(expected_buckets):
            result[StdLib.get_rounded_double(i / 10)] = 0.0

        sum_beyond_range = 0
        # Fill in the observed values to the dictionary
        for item in percentages_histogram:
            if item >= expected_buckets / 10:
                # Accumulate anything above X milliseconds
                sum_beyond_range += percentages_histogram.get(item)
                continue
            index = StdLib.get_rounded_double(item)
            result[index] = percentages_histogram.get(item)

        last_bucket = StdLib.get_rounded_double((expected_buckets - 1) / 10)
        result[last_bucket] += sum_beyond_range

        return result