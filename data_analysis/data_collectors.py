from functools import reduce
from pathlib import Path

import pandas as pd

from path_helper import PathHelper
from file_parsers import MemtierParser, MiddlewareParser


class DataCollector:

    def __init__(self, experiment_dict):
        self.exp_desc = experiment_dict
        self.path_helper = PathHelper(self.exp_desc)
        self.memtier_client_paths = {}
        self.middleware_paths = {}
        self.memtier_raw_results = {}
        self.middleware_raw_results = {}
        self.dataframe_get = None
        self.dataframe_set = None
        self.dataframe_histogram_set = None
        self.dataframe_histogram_get = None

    def infer_paths(self):
        """
        Infers and sets all paths such that future calls to this object can consume the list and do File I/O which
        allows generating averages and std-deviations.
        :return: Nothing
        """
        exp_paths = self.path_helper.generate_paths_by_repetition_distinct_hosts_after_types()
        clients = [actor for actor in self.exp_desc['hostnames'] if actor.startswith('Client')]
        exp_client_paths = [self.path_helper.filter_paths_for_creator(exp_paths, client)
                            for client in clients]
        self.memtier_client_paths = dict(zip(clients, exp_client_paths))
        middlewares = [actor for actor in self.exp_desc['hostnames'] if actor.startswith('Middleware')]
        exp_middleware_paths = [self.path_helper.filter_paths_for_creator(exp_paths, middleware)
                                for middleware in middlewares]
        self.middleware_paths = dict(zip(middlewares, exp_middleware_paths))

    def get_raw_results(self, target, histograms):
        """
        Does File I/O and parses summaries plus histograms in a list
        :return: Nothing
        """
        req_type = {t: {} for t in self.exp_desc['request_types']}

        for key in req_type.keys():
            req_type[key] = {wt: {} for wt in self.exp_desc['worker_threads']}

        memtier_count = len([c for c in self.exp_desc['hostnames'] if 'Client' in c])
        for r_type in req_type.keys():
            for wt in req_type[r_type].keys():
                req_type[r_type][wt] = {cpm * 2 * memtier_count: {} for cpm in self.exp_desc['memtier_clients']}

        if target == 'Memtier':
            for key, val in self.memtier_client_paths.items():
                for path in val:
                    interpreted_path = PathHelper.interpret_path(path)
                    r_type = interpreted_path['type']
                    wt_count = int(interpreted_path['wt'])
                    tc_count = (int(interpreted_path['vc']) * 2 * memtier_count)
                    hostname = interpreted_path['hostname']
                    if hostname not in req_type[r_type][wt_count][tc_count]:
                        req_type[r_type][wt_count][tc_count][hostname] = {}
                    repetition = str(interpreted_path['rep'])
                    if repetition not in req_type[r_type][wt_count][tc_count][hostname]:
                        req_type[r_type][wt_count][tc_count][hostname][repetition] = {}

                    if Path(path).exists():
                        memtier_instances = []
                        for memtier_target in self.exp_desc['memtier_targets']:
                            memtier_parser = MemtierParser()
                            memtier_parser.parse_file(path, memtier_target, histograms=histograms)
                            memtier_instances.append(memtier_parser)
                        memtier_experiment_results = reduce(lambda item1, item2: item1 + item2, memtier_instances)
                        req_type[r_type][wt_count][tc_count][hostname][repetition] = memtier_experiment_results
            self.memtier_raw_results = req_type
        else:
            for key, val in self.middleware_paths.items():
                for path in val:
                    interpreted_path = PathHelper.interpret_path(path)
                    r_type = interpreted_path['type']
                    wt_count = int(interpreted_path['wt'])
                    tc_count = (int(interpreted_path['vc']) * 2 * memtier_count)
                    hostname = interpreted_path['hostname']
                    if hostname not in req_type[r_type][wt_count][tc_count]:
                        req_type[r_type][wt_count][tc_count][hostname] = {}
                    repetition = str(interpreted_path['rep'])
                    if repetition not in req_type[r_type][wt_count][tc_count][hostname]:
                        req_type[r_type][wt_count][tc_count][hostname][repetition] = {}

                    if Path(path.joinpath('mw-stats')).exists():
                        middleware_parser = MiddlewareParser()
                        middleware_parser.parse_dir(path, memtier_count, histograms=histograms)
                        req_type[r_type][wt_count][tc_count][hostname][repetition] = middleware_parser
            self.middleware_raw_results = req_type


class MemtierCollector(DataCollector):

    def __init__(self, exp_dict):
        super().__init__(exp_dict)

    def get_raw_results(self, histograms, target='Memtier'):
        self.histogram_data = histograms
        super().get_raw_results(target, histograms)

    def construct_dataframes(self):
        self.construct_summary_dataframe()
        if self.histogram_data:
            self.construct_histogram_dataframe()

    def construct_summary_dataframe(self):
        """
        Construct out of the raw data a data frame (maybe multi-index) which can be used by seaborn for plotting
        :return: Nothing.
        """
        df_rows_set = []
        df_rows_get = []
        for req, req_dict in self.memtier_raw_results.items():
            for wc, wc_dict in req_dict.items():
                for tc, tc_dict in wc_dict.items():
                    for host, host_dict in tc_dict.items():
                        for rep, mt_result in host_dict.items():
                            if mt_result:
                                mt_dict = mt_result.get_as_dict()
                                single_row = {'Type': req,
                                              'Worker_Threads': int(wc),
                                              'Num_Clients': int(tc),
                                              'Host': host,
                                              'Repetition': int(rep),
                                              'Seconds': mt_dict['Seconds']}
                                row_copy_interactive = single_row.copy()

                                if req is 'GET':
                                    for key, value in mt_dict[req + '_Observed'].items():
                                        single_row[key] = value
                                    df_rows_get.append(single_row)

                                    row_copy_interactive['Type'] = req + '_Interactive'
                                    for key, value in mt_dict[req + '_Interactive'].items():
                                        row_copy_interactive[key] = value
                                    df_rows_get.append(row_copy_interactive)
                                    continue
                                if req is 'SET':
                                    for key, value in mt_dict[req + '_Observed'].items():
                                        single_row[key] = value
                                    df_rows_set.append(single_row)

                                    row_copy_interactive['Type'] = req + '_Interactive'
                                    for key, value in mt_dict[req + '_Interactive'].items():
                                        row_copy_interactive[key] = value
                                    df_rows_set.append(row_copy_interactive)
                                    continue

                                # We have a multi-type experiment, let's store the numbers in their respective tables
                                single_row_get = single_row.copy()
                                row_copy_get = single_row_get.copy()
                                for key, value in mt_dict['GET_Observed'].items():
                                    single_row_get[key] = value
                                df_rows_get.append(single_row_get)

                                row_copy_get['Type'] = req + '_Interactive'
                                for key, value in mt_dict['GET_Interactive'].items():
                                    row_copy_get[key] = value
                                df_rows_get.append(row_copy_interactive)

                                for key, value in mt_dict['SET_Observed'].items():
                                    single_row[key] = value
                                df_rows_set.append(single_row)

                                row_copy_interactive['Type'] = req + '_Interactive'
                                for key, value in mt_dict['SET_Interactive'].items():
                                    row_copy_interactive[key] = value
                                df_rows_set.append(row_copy_interactive)

        self.dataframe_set = pd.DataFrame(df_rows_set)
        self.dataframe_get = pd.DataFrame(df_rows_get)

    def construct_histogram_dataframe(self):
        """
        Construct out of the raw data a data frame (maybe multi-index) which can be used by seaborn for plotting
        :return: Nothing.
        """
        df_rows_set = []
        df_rows_get = []
        for req, req_dict in self.memtier_raw_results.items():
            for wc, wc_dict in req_dict.items():
                for tc, tc_dict in wc_dict.items():
                    for host, host_dict in tc_dict.items():
                        for rep, mt_result in host_dict.items():
                            if mt_result:
                                mt_dict = mt_result.get_as_dict()

                                single_row = {'Type': req,
                                              'Worker_Threads': int(wc),
                                              'Num_Clients': int(tc),
                                              'Host': host,
                                              'Repetition': int(rep),
                                              'Seconds': mt_dict['Seconds']}

                                if req is 'GET':
                                    for key, value in mt_dict['GET_Histogram_Percentage'].items():
                                        row_copy = single_row.copy()
                                        row_copy['Bucket'] = key
                                        row_copy['Percentage'] = value
                                        row_copy['Count'] = mt_dict['GET_Histogram_Count'][key]
                                        df_rows_get.append(row_copy)
                                    continue
                                if req is 'SET':
                                    for key, value in mt_dict['SET_Histogram_Percentage'].items():
                                        row_copy = single_row.copy()
                                        row_copy['Bucket'] = key
                                        row_copy['Percentage'] = value
                                        row_copy['Count'] = mt_dict['SET_Histogram_Count'][key]
                                        df_rows_set.append(row_copy)
                                    continue

                                # We have a multi-type experiment, let's store the numbers in their respective tables
                                for key, value in mt_dict['GET_Histogram_Percentage'].items():
                                    row_copy = single_row.copy()
                                    row_copy['Bucket'] = key
                                    row_copy['Percentage'] = value
                                    row_copy['Count'] = mt_dict['GET_Histogram_Count'][key]
                                    df_rows_get.append(row_copy)

                                for key, value in mt_dict['SET_Histogram_Percentage'].items():
                                    row_copy = single_row.copy()
                                    row_copy['Bucket'] = key
                                    row_copy['Percentage'] = value
                                    row_copy['Count'] = mt_dict['SET_Histogram_Count'][key]
                                    df_rows_set.append(row_copy)

        self.dataframe_histogram_set = pd.DataFrame(df_rows_set)
        self.dataframe_histogram_get = pd.DataFrame(df_rows_get)

    def generate_dataframe(self, histograms):
        self.infer_paths()
        self.get_raw_results(histograms)
        self.construct_dataframes()


class MiddlewareCollector(DataCollector):

    def __init__(self, exp_dict):
        super().__init__(exp_dict)

    def get_raw_results(self, histograms, target='Middleware'):
        self.histogram_data = histograms
        super().get_raw_results(target, histograms)

    def construct_dataframes(self):
        self.construct_summary_dataframe()
        if self.histogram_data:
            self.construct_histogram_dataframe()

    def construct_summary_dataframe(self):
        """
        Construct out of the raw data a data frame (maybe multi-index) which can be used by seaborn for plotting
        :return: Nothing.
        """
        df_rows_set = []
        df_rows_get = []
        for req, req_dict in self.middleware_raw_results.items():
            for wc, wc_dict in req_dict.items():
                for tc, tc_dict in wc_dict.items():
                    for host, host_dict in tc_dict.items():
                        for rep, mt_result in host_dict.items():
                            if mt_result:
                                mt_dict = mt_result.get_as_dict()
                                single_row = {'Type': req,
                                              'Worker_Threads': int(wc),
                                              'Num_Clients': int(tc),
                                              'Host': host,
                                              'Repetition': int(rep),
                                              'Seconds': mt_dict['Seconds']}
                                row_copy_interactive = single_row.copy()

                                if req is 'GET':
                                    for key, value in mt_dict[req + '_Observed'].items():
                                        single_row[key] = value
                                    df_rows_get.append(single_row)

                                    row_copy_interactive['Type'] = req + '_Interactive'
                                    for key, value in mt_dict[req + '_Interactive'].items():
                                        row_copy_interactive[key] = value
                                    df_rows_get.append(row_copy_interactive)
                                    continue
                                if req is 'SET':
                                    for key, value in mt_dict[req + '_Observed'].items():
                                        single_row[key] = value
                                    df_rows_set.append(single_row)

                                    row_copy_interactive['Type'] = req + '_Interactive'
                                    for key, value in mt_dict[req + '_Interactive'].items():
                                        row_copy_interactive[key] = value
                                    df_rows_set.append(row_copy_interactive)
                                    continue

                                # We have a multi-type experiment, let's store the numbers in their respective tables
                                single_row_get = single_row.copy()
                                row_copy_get = single_row_get.copy()
                                for key, value in mt_dict['GET_Observed'].items():
                                    single_row_get[key] = value
                                df_rows_get.append(single_row_get)

                                row_copy_get['Type'] = req + '_Interactive'
                                for key, value in mt_dict['GET_Interactive'].items():
                                    row_copy_get[key] = value
                                df_rows_get.append(row_copy_interactive)

                                for key, value in mt_dict['SET_Observed'].items():
                                    single_row[key] = value
                                df_rows_set.append(single_row)

                                row_copy_interactive['Type'] = req + '_Interactive'
                                for key, value in mt_dict['SET_Interactive'].items():
                                    row_copy_interactive[key] = value
                                df_rows_set.append(row_copy_interactive)

        self.dataframe_set = pd.DataFrame(df_rows_set)
        self.dataframe_get = pd.DataFrame(df_rows_get)

    def construct_histogram_dataframe(self):
        """
        Construct out of the raw data a data frame (maybe multi-index) which can be used by seaborn for plotting
        :return: Nothing.
        """
        df_rows_set = []
        df_rows_get = []
        for req, req_dict in self.middleware_raw_results.items():
            for wc, wc_dict in req_dict.items():
                for tc, tc_dict in wc_dict.items():
                    for host, host_dict in tc_dict.items():
                        for rep, mt_result in host_dict.items():
                            if mt_result:
                                mt_dict = mt_result.get_as_dict()

                                single_row = {'Type': req,
                                              'Worker_Threads': int(wc),
                                              'Num_Clients': int(tc),
                                              'Host': host,
                                              'Repetition': int(rep),
                                              'Seconds': mt_dict['Seconds']}

                                if req is 'GET':
                                    for key, value in mt_dict['GET_Histogram_Percentage'].items():
                                        row_copy = single_row.copy()
                                        row_copy['Bucket'] = key
                                        row_copy['Percentage'] = value
                                        row_copy['Count'] = mt_dict['GET_Histogram_Count'][key]
                                        df_rows_get.append(row_copy)
                                    continue
                                if req is 'SET':
                                    for key, value in mt_dict['SET_Histogram_Percentage'].items():
                                        row_copy = single_row.copy()
                                        row_copy['Bucket'] = key
                                        row_copy['Percentage'] = value
                                        row_copy['Count'] = mt_dict['SET_Histogram_Count'][key]
                                        df_rows_set.append(row_copy)
                                    continue

                                # We have a multi-type experiment, let's store the numbers in their respective tables
                                for key, value in mt_dict['GET_Histogram_Percentage'].items():
                                    row_copy = single_row.copy()
                                    row_copy['Bucket'] = key
                                    row_copy['Percentage'] = value
                                    row_copy['Count'] = mt_dict['GET_Histogram_Count'][key]
                                    df_rows_get.append(row_copy)

                                for key, value in mt_dict['SET_Histogram_Percentage'].items():
                                    row_copy = single_row.copy()
                                    row_copy['Bucket'] = key
                                    row_copy['Percentage'] = value
                                    row_copy['Count'] = mt_dict['SET_Histogram_Count'][key]
                                    df_rows_set.append(row_copy)

        self.dataframe_histogram_set = pd.DataFrame(df_rows_set)
        self.dataframe_histogram_get = pd.DataFrame(df_rows_get)

    def generate_dataframe(self, histograms):
        self.infer_paths()
        self.get_raw_results(histograms)
        self.construct_dataframes()
