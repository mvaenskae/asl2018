import collections
import math
import re
import sys


class MemtierParser:

    def __init__(self):
        self.seconds = 0

        # Summary tuples
        self.set_summary = ()
        self.get_summary = ()
        self.total_summary = ()

        # Histogram of query types based on percentages
        self.get_histogram_percentage = {}
        self.set_histogram_percentage = {}

        # Histogram of query types based on counts
        self.set_histogram_counts = {}
        self.get_histogram_counts = {}

    def parse_file(self, argc, argv):
        # Second argv is filename
        if argc > 3 or argc < 2:
            sys.exit("Invalid number of arguments. Expecting 2")

        set_histogram_memtier = [(0, 0)]
        get_histogram_memtier = [(0, 0)]

        regex_seconds = r"^\d+\s+Seconds"

        regex_set_results = r"^Sets"
        regex_get_results = r"^Gets"
        regex_totals_results = r"^Totals"

        regex_get_histogram_entry = r"^GET\s+"
        regex_set_histogram_entry = r"^SET\s+"

        with open(argv[1], "r") as file:
            for line in file:
                if re.match(regex_seconds, line):
                    self.seconds = MemtierParser.get_sane_int(line.split()[0])
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

        self.get_histogram_percentage = MemtierParser.transform_to_our_buckets(memtier_get_histogram_percentage)

        self.set_histogram_percentage = MemtierParser.transform_to_our_buckets(memtier_set_histogram_percentage)
        self.get_histogram_percentage = MemtierParser.transform_to_our_buckets(memtier_get_histogram_percentage)

        self.set_histogram_counts = MemtierParser.percentages_to_counts(self.set_histogram_percentage, self.set_summary[0] * self.seconds)
        self.get_histogram_counts = MemtierParser.percentages_to_counts(self.get_histogram_percentage, self.get_summary[0] * self.seconds)

    @staticmethod
    def parse_result_line(line):
        """
        Function to parse a memtier summary line, returns any non-valid numbers with default value 0.0.
        :param line: Line to parse (guaranteed by caller to be a valid summary line).
        :return: Parsed elements as tuples of doubles.
        """
        type, throughput, hits, misses, latency, data_throughput = line.split()

        throughput = MemtierParser.get_sane_double(throughput)
        hits = MemtierParser.get_sane_double(hits)
        misses = MemtierParser.get_sane_double(misses)
        latency = MemtierParser.get_sane_double(latency)
        data_throughput = MemtierParser.get_sane_double(data_throughput)

        return throughput, hits, misses, latency, data_throughput

    @staticmethod
    def parse_histogram_entry(line):
        """
        Function to parse a memtier histogram line, returns a valid tuple.
        :param line: Line to parse (guaranteed by caller to be a valid histogram line).
        :return: Parsed elements as tuples of doubles.
        """
        type, bucket, cdf_until_and_including_bucket = line.split()

        bucket = MemtierParser.get_sane_double(bucket)
        cdf_until_and_including_bucket = MemtierParser.get_sane_double(cdf_until_and_including_bucket)

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

        # Default initialize the result-dictionary to our needs
        for i in range(500):
            result[MemtierParser.get_rounded_double(i / 10)] = 0.0

        sum_over_50 = 0
        # Fill in the observed values to the dictionary
        for item in percentages_memtier:
            if item >= 50.0:
                # Accumulate anything above 50 milliseconds
                sum_over_50 += percentages_memtier.get(item)
            else:
                # Try to expand buckets and divide respective percentages evenly -> uniform distribution inside buckets
                bucket_count = int(math.pow(10, math.ceil(math.log10(item)) - 1))
                bucket_value = MemtierParser.safe_div(percentages_memtier.get(item), bucket_count)
                for i in range(bucket_count):
                    index = MemtierParser.get_rounded_double(item + i/10)
                    result[index] = bucket_value

        result['49.9'] += sum_over_50

        return result

    @staticmethod
    def percentages_to_counts(percentage_histogram, op_count):
        """
        Function to transform an dictionary of "timestamp: tuple" with percentages to counts.
        :param percentage_histogram: Dictionary with percentages (non cumulative).
        :return: Dictionary of "timestamp: counts"
        """
        return {ts: percentage_histogram[ts]/100 * op_count for ts in percentage_histogram}

    @staticmethod
    def pretty_print_list(formatted_histogram):
        """
        Get a list of doubles to two decimals after the comma for each element in the tuple.
        :param formatted_histogram:
        :return: Array of tuples (timestamp, distribution) up to two decimal places.
        """
        return ["(%.1f, %.2f)" % (timestamp, distribution) for (timestamp, distribution) in formatted_histogram]

    @staticmethod
    def get_sane_double(s):
        """
        Get an interpreted double for the given string. If it doesn't parse return 0.0.
        :param s: String to parse as double.
        :return: An interpreted double or 0.0 (if not convertible).
        """
        try:
            float(s)
            return float(s)
        except ValueError:
            return 0.0

    @staticmethod
    def get_sane_int(s):
        """
        Get an interpreted integer for the given string. If it doesn't parse return 0.
        :param s: String to parse as integer.
        :return: An interpreted integer or 0 (if not convertible).
        """
        try:
            int(s)
            return int(s)
        except ValueError:
            return 0

    @staticmethod
    def get_rounded_double(s):
        """
        Get a string representation of a rounded double up to 1 decimal place.
        :param s: string to be interpreted as a float
        :return: String representaton of `s` or 0.0 if unparseable.
        """
        try:
            float(s)
            return "{0:.1f}".format(float(s))
        except ValueError:
            return "0.0"

    @staticmethod
    def safe_div(x, y):
        """
        Helper method to safely divide two numbers
        :param x: Dividend
        :param y: Divisor
        :return: Division of 0 if divisior is 0.
        """
        if y == 0:
            return 0
        return x / y

    def print_parsed(self):
        print(self.seconds)
        print(self.set_summary)
        print(self.get_summary)
        print(self.total_summary)
        print(self.set_histogram_percentage)
        print(self.get_histogram_percentage)
        print(self.set_histogram_counts)
        print(self.get_histogram_counts)


if __name__ == '__main__':
    file_results = MemtierParser()
    file_results.parse_file(len(sys.argv), sys.argv)
    file_results.print_parsed()
