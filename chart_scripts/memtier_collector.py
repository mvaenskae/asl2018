import memtier_parser as mp
import path_helper as pt
import experiment_definitions as ed

import os

import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from scipy import stats

class MemtierCollector:

    def __init__(self, experiment_dict):
        self.exp_desc = experiment_dict
        self.path_helper = pt.PathHelper(self.exp_desc)
        self.memtier_results = dict.fromkeys(self.exp_desc['hostnames'])
        self.memtier_client_paths = {}

    def infer_paths(self):
        """
        Infers and sets all paths such that future calls to this object can consume the list and do File I/O which
        allows generating averages and std-deviations.
        :return: Nothing
        """
        exp_paths = self.path_helper.generate_paths_by_repetition_distinct_hosts_after_types()
        exp_client_paths = [self.path_helper.filter_paths_for_creator(exp_paths, client_name)
                            for client_name
                            in self.exp_desc['hostnames']
                            if client_name.startswith('Client')]
        self.memtier_client_paths = dict(zip(list(self.memtier_results.keys()), exp_client_paths))

    def get_raw_results(self):
        """
        Does File I/O and parses summaries plus histograms in a list
        :return: Nothing
        """
        return

    def average_results(self):
        """
        Averages raw results stored and stores std-dev.
        :return: Nothing
        """

    def get_average_get_summary(self):
        """
        Return averaged get summaries
        :return:
        """
        return []

    def get_average_set_summary(self):
        """
        Return averaged set summaries
        :return:
        """
        return []

    def get_histogram_percentage_get(self):
        """
        Return averaged get histogram with percentages
        :return:
        """
        return []

    def get_histogram_percentage_set(self):
        """
        Return averaged set histogram with percentages
        :return:
        """
        return []


if __name__ == '__main__':

    memtier_collector = MemtierCollector(ed.ExperimentDefinitions.subexpriment_21())
    memtier_collector.infer_paths()
    print(memtier_collector.memtier_results)



    #sns.set(style="darkgrid", color_codes=True)

    # Load an example dataset with long-form data
    #fmri = sns.load_dataset("fmri")
    #print(fmri)

    # Plot the responses for different events and regions
    # sns.lineplot(x="timepoint", y="signal",
    #              hue="region", style="event",
    #              data=fmri)

    #x = np.random.normal(size=100)
    #sns.distplot(x)
    #plt.show()