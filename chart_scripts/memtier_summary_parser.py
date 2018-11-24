import os
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
        self.get_histogram_percentage = [(0.0, 0.0)]
        self.set_histogram_percentage = [(0.0, 0.0)]

        # Histogram of query types based on counts
        self.set_histogram_counts = [(0.0, 0.0)]
        self.get_histogram_counts = [(0.0, 0.0)]

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

        self.set_histogram_percentage = MemtierParser.transform_from_cdf(set_histogram_memtier)
        self.get_histogram_percentage = MemtierParser.transform_from_cdf(get_histogram_memtier)

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
    def percentages_to_counts(percentage_histogram, op_count):
        """
        Function to transform an array of tuples with percentages to counts.
        :param percentage_histogram: Array of tuples with percentages (non cumulative).
        :return: Array of tuples (timestamp_range, counts)
        """
        return [(timestamp, percentage * op_count) for (timestamp, percentage) in percentage_histogram]

    @staticmethod
    def pretty_print_list(formatted_histogram):
        """
        Get a list of doubles to two decimals after the comma for each element in the tuple.
        :param formatted_histogram:
        :return: Array of tuples (timestamp, distribution) up to two decimal places.
        """
        return ["(%.2f, %.2f)" % (timestamp, distribution) for (timestamp, distribution) in formatted_histogram]

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

    def print_parsed(self):
        print(self.seconds)
        print(self.set_summary)
        print(self.get_summary)
        print(self.total_summary)
        print(MemtierParser.pretty_print_list(self.set_histogram_percentage))
        print(MemtierParser.pretty_print_list(self.get_histogram_percentage))
        print(MemtierParser.pretty_print_list(self.set_histogram_counts))
        print(MemtierParser.pretty_print_list(self.get_histogram_counts))


if __name__ == '__main__':
    file_results = MemtierParser()
    file_results.parse_file(len(sys.argv), sys.argv)
    file_results.print_parsed()
