from functools import reduce

import memtier_parser as mp
import path_helper as pt
import experiment_definitions as ed

import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import numpy as np


class MemtierCollector:

    def __init__(self, experiment_dict):
        self.exp_desc = experiment_dict
        self.path_helper = pt.PathHelper(self.exp_desc)
        self.memtier_client_paths = {}
        self.memtier_raw_results = {}
        self.dataframe_get = None
        self.dataframe_set = None

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
        self.memtier_client_paths = dict(zip(self.exp_desc['hostnames'], exp_client_paths))

    def get_raw_results(self, histograms):
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

        for key, val in self.memtier_client_paths.items():
            for path in val:
                interpreted_path = pt.PathHelper.interpret_path(path)
                r_type = interpreted_path['type']
                wt_count = int(interpreted_path['wt'])
                tc_count = (int(interpreted_path['vc']) * 2 * memtier_count)
                hostname = interpreted_path['hostname']
                if hostname not in req_type[r_type][wt_count][tc_count]:
                    req_type[r_type][wt_count][tc_count][hostname] = {}
                repetition = str(interpreted_path['rep'])
                if repetition not in req_type[r_type][wt_count][tc_count][hostname]:
                    req_type[r_type][wt_count][tc_count][hostname][repetition] = {}

                memtier_instances = []
                for memtier_target in self.exp_desc['memtier_targets']:
                    memtier_parser = mp.MemtierParser()
                    memtier_parser.parse_file(path, memtier_target, histograms=histograms)
                    memtier_instances.append(memtier_parser)

                memtier_experiment_results = reduce(lambda item1, item2: item1 + item2, memtier_instances)
                req_type[r_type][wt_count][tc_count][hostname][repetition] = memtier_experiment_results

        self.memtier_raw_results = req_type


class MemtierSummary(MemtierCollector):

    def __init__(self, exp_dict):
        super().__init__(exp_dict)

    def get_raw_results(self, histograms=False):
        super().get_raw_results(False)

    def construct_dataframe(self):
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
                            mt_dict = mt_result.get_as_dict()
                            single_row = {'Type': req,
                                          'Worker_Threads': int(wc),
                                          'Num_Clients': int(tc),
                                          'Host': host,
                                          'Repetition': int(rep),
                                          'Seconds': mt_dict['Seconds']}

                            if req is 'GET':
                                for key, value in mt_dict['GET_Summary'].items():
                                    single_row[key] = value
                                df_rows_get.append(single_row)
                                continue
                            if req is 'SET':
                                for key, value in mt_dict['SET_Summary'].items():
                                    single_row[key] = value
                                df_rows_set.append(single_row)
                                continue

                            # We have a multi-type experiment, let's store the numbers in their respective tables
                            single_row_get = single_row.copy()
                            for key, value in mt_dict['GET_Summary'].items():
                                single_row_get[key] = value
                            df_rows_get.append(single_row_get)

                            for key, value in mt_dict['SET_Summary'].items():
                                single_row[key] = value
                            df_rows_set.append(single_row)

        self.dataframe_set = pd.DataFrame(df_rows_set)
        self.dataframe_get = pd.DataFrame(df_rows_get)

    def generate_dataframe(self):
        self.infer_paths()
        self.get_raw_results()
        self.construct_dataframe()


class MemtierHistogram(MemtierCollector):

    def __init__(self, exp_dict):
        super().__init__(exp_dict)

    def get_raw_results(self, histograms=True):
        super().get_raw_results(True)

    def construct_dataframe(self):
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

        self.dataframe_set = pd.DataFrame(df_rows_set)
        self.dataframe_get = pd.DataFrame(df_rows_get)

    def generate_dataframe(self):
        self.infer_paths()
        self.get_raw_results()
        self.construct_dataframe()


def get_average_and_std(dataframe, aggregate_on):
    return dataframe[aggregate_on].agg(['mean', 'std']).rename(index=str, columns={"mean": aggregate_on + '_Mean',
                                                                                   "std": aggregate_on + '_Std'})

def get_sum(dataframe, aggregate_on):
    return dataframe[aggregate_on].agg(['sum']).reset_index().rename(index=str, columns={"sum": aggregate_on})


def get_average(dataframe, aggregate_on):
    return dataframe[aggregate_on].agg(['mean']).reset_index().rename(index=str, columns={"mean": aggregate_on})


def get_percentiles(dataframe):
    return dataframe.quantile(([.25, .50, .75, .90, .99])).reset_index().rename(index=str,
                                                                                columns={"level_2": 'Percentile'})


def memtier_experiment(experiment_definition, histogram=False):
    if not histogram:
        memtier_collector = MemtierSummary(experiment_definition)
    else:
        memtier_collector = MemtierHistogram(experiment_definition)

    memtier_collector.generate_dataframe()
    return [memtier_collector.dataframe_set, memtier_collector.dataframe_get]


def _lineplot(dataframe, experiment_title, save_as_filename,
              x=None, y=None, hue=None, style=None, ci='sd', err_style='bars',
              xlabel=None, ylabel=None, huelabel=None, stylelabel=None,
              xlim=(0, None), ylim=(0, None),
              xticks=None):
    sns.lineplot(x, y, data=dataframe, legend="full", hue=hue, style=style,
                 ci=ci, err_style=err_style).set(xlabel=xlabel, ylabel=ylabel,
                                                 title=experiment_title,
                                                 xlim=xlim, ylim=ylim,)
    sns.scatterplot(x, y, data=dataframe, legend="full", hue=hue, style=style,
                    ci=None).set(xlabel=xlabel, ylabel=ylabel,
                                 title=experiment_title,
                                 xlim=xlim, ylim=ylim)
    if isinstance(xticks, tuple):
        plt.xticks(xticks[0], xticks[1])
    else:
        plt.xticks(xticks)
    if huelabel is not None or stylelabel is not None:
        legend = plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        for txt in legend.get_texts():
            if txt.get_text() is hue and huelabel is not None:
                txt.set_text(huelabel)
                continue
            if txt.get_text() is style and stylelabel is not None:
                txt.set_text(stylelabel)
                continue
    plt.show()
    # plt.savefig(save_as_filename)


def plot_throughput(dataframe, experiment_title, save_as_filename,
                    x='Num_Clients', y='Request_Throughput', hue='RequestType', style='Worker_Threads', ci='sd',
                    err_style='bars',
                    xlabel='Memtier Client Count', ylabel='Throughput (req/s)', huelabel='Request Type',
                    stylelabel='Worker Threads',
                    xlim=(0, None), ylim=(0, None),
                    xticks=None):
    if xticks is None and x == 'Num_Clients':
        xticks = dataframe.Num_Clients.unique()
    _lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style, xlabel, ylabel, huelabel,
              stylelabel, xlim, ylim, xticks)


def plot_throughput_single(dataframe, experiment_title, save_as_filename,
                           x='Num_Clients', y='Request_Throughput', hue='Worker_Threads', style=None,
                           ci='sd', err_style='bars',
                           xlabel='Memtier Client Count', ylabel='Throughput (req/s)', huelabel='Worker Threads',
                           stylelabel=None,
                           xlim=(0, None), ylim=(0, None),
                           xticks=None):
    if xticks is None and x == 'Num_Clients':
        xticks = dataframe.Num_Clients.unique()
    _lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style, xlabel, ylabel, huelabel,
              stylelabel, xlim, ylim, xticks)


def plot_latency(dataframe, experiment_title, save_as_filename,
                 x='Num_Clients', y='Latency', hue='RequestType', style='Worker_Threads', ci='sd', err_style='bars',
                 xlabel='Memtier Client Count', ylabel='Latency (ms)', huelabel='Request Type',
                 stylelabel='Worker Threads',
                 xlim=(0, None), ylim=(0, None),
                 xticks=None):
    if xticks is None and x == 'Num_Clients':
        xticks = dataframe.Num_Clients.unique()
    _lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style, xlabel, ylabel, huelabel,
              stylelabel, xlim, ylim, xticks)


def plot_latency_single(dataframe, experiment_title, save_as_filename,
                        x='Num_Clients', y='Latency', hue='Worker_Threads', style=None, ci='sd',
                        err_style='bars',
                        xlabel='Memtier Client Count', ylabel='Latency (ms)', huelabel='Worker Threads',
                        stylelabel=None,
                        xlim=(0, None), ylim=(0, None),
                        xticks=None):
    if xticks is None and x == 'Num_Clients':
        xticks = dataframe.Num_Clients.unique()
    _lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style, xlabel, ylabel, huelabel,
              stylelabel, xlim, ylim, xticks)


def throughput_latency_get_set(subexperiment, plot=True):
    exp_name = "Experiment {}.{}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
    flattened = memtier_experiment(subexperiment)

    set_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads'])
    get_group = flattened[1].groupby(['Num_Clients', 'Repetition', 'Worker_Threads'])

    summed_set_throughput = get_sum(set_group, 'Request_Throughput')
    summed_get_throughput = get_sum(get_group, 'Request_Throughput')
    average_set_latency = get_average(set_group, 'Latency')
    average_get_latency = get_average(get_group, 'Latency')

    concatenated_throughput = pd.concat(
        [summed_set_throughput.assign(RequestType='SET'), summed_get_throughput.assign(RequestType='GET')])
    concatenated_latency = pd.concat(
        [average_set_latency.assign(RequestType='SET'), average_get_latency.assign(RequestType='GET')])

    if plot:
        plot_throughput(concatenated_throughput, exp_name, None)
        plot_latency(concatenated_latency, exp_name, None)

    plotted_throughput_set = summed_set_throughput.groupby(['Num_Clients', 'Worker_Threads'])
    plotted_throughput_get = summed_get_throughput.groupby(['Num_Clients', 'Worker_Threads'])
    plotted_latency_set = average_set_latency.groupby(['Num_Clients', 'Worker_Threads'])
    plotted_latency_get = average_get_latency.groupby(['Num_Clients', 'Worker_Threads'])

    throughput_flattened_set = get_average_and_std(plotted_throughput_set, 'Request_Throughput')
    throughput_flattened_get = get_average_and_std(plotted_throughput_get, 'Request_Throughput')
    latency_flattened_set = get_average_and_std(plotted_latency_set, 'Latency')
    latency_flattened_get = get_average_and_std(plotted_latency_get, 'Latency')

    print(exp_name + " SET:")
    print(throughput_flattened_set)
    print("====================\n")

    print(exp_name + " GET:")
    print(throughput_flattened_get)
    print("====================\n")

    print(exp_name + " SET:")
    print(latency_flattened_set)
    print("====================\n")

    print(exp_name + " GET:")
    print(latency_flattened_get)
    print("====================\n")


def throughput_latency_single_request_type(subexperiment, type='SET', plot=True):
    exp_name = "Experiment {}.{}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
    flattened = memtier_experiment(subexperiment)

    set_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads'])

    summed_set_throughput = get_sum(set_group, 'Request_Throughput')
    average_set_latency = get_average(set_group, 'Latency')

    concatenated_throughput = pd.concat([summed_set_throughput.assign(RequestType=type)])
    concatenated_latency = pd.concat([average_set_latency.assign(RequestType=type)])

    if plot:
        plot_throughput_single(concatenated_throughput, exp_name, None)
        plot_latency_single(concatenated_latency, exp_name, None)

    plotted_throughput_set = summed_set_throughput.groupby(['Num_Clients', 'Worker_Threads'])
    plotted_latency_set = average_set_latency.groupby(['Num_Clients', 'Worker_Threads'])

    throughput_flattened_set = get_average_and_std(plotted_throughput_set, 'Request_Throughput')
    latency_flattened_set = get_average_and_std(plotted_latency_set, 'Latency')

    print(exp_name + " " + type + ":")
    print(throughput_flattened_set)
    print("====================\n")

    print(exp_name + " " + type + ":")
    print(latency_flattened_set)
    print("====================\n")


def histogram_percentiles(subexperiment, plot=True):
    exp_name = "Experiment {}.{}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
    flattened = memtier_experiment(subexperiment, histogram=True)

    if subexperiment['subexperiment_id'] == 2:
        req_type = 'MULTIGET_6'
    else:
        req_type = 'SHARDED_6'

    summed_get_bucket = (flattened[1].loc[flattened[1]['Type'] == req_type]).groupby(['Bucket', 'Repetition']).agg(
        {'Count': np.sum}).reset_index()
    summed_get_bucket['Bucket_Double'] = summed_get_bucket['Bucket'].str.extract('(\d+.\d)', expand=False).astype(
        np.float64)
    average_buckets = summed_get_bucket.groupby(['Bucket_Double'])['Count'].agg(['mean'])

    occurence_list = []
    for _, row in average_buckets.iterrows():
        occurences = [row.name] * int(row['mean'])
        if int(row['mean']) > 0:
            occurence_list.extend(occurences)
        else:
            occurence_list.append(row.name) # stability of bucket visualization

    histogram = pd.DataFrame(occurence_list)

    if plot:
        sns.distplot(histogram, bins=200, kde=False).set(xlabel='Bucket (ms)', ylabel='GET Request Count',
                                                         title=exp_name,
                                                         xlim=(0, 20), ylim=(0, 6000))
        plt.xticks(np.arange(0, 20.1, step=2.5), np.linspace(0, 20, 9))
        plt.show()

    summed_get_bucket = flattened[1].groupby(['Bucket', 'Repetition', 'Type']).agg({'Count': np.sum}).reset_index()

    histograms = []
    keys_iterated = []
    for req_type in subexperiment['request_types']:
        for rep in range(1, 4):
            histograms.append(summed_get_bucket.loc[(summed_get_bucket['Type'] == req_type) & (summed_get_bucket['Repetition'] == rep)])
            keys_iterated.append({'Request_Type': req_type, 'Repetition': rep})

    occurences_list = []
    for item in histograms:
        occurence_list = []
        for _, row in item.iterrows():
            occurences = [float(row['Bucket'])] * int(row['Count'])
            if int(row['Count']) > 0:
                occurence_list.extend(occurences)
        occurences_list.append(occurence_list)

    raw_hits_dataframe = []
    for occurence_hits, exp_desc_tuple in zip(occurences_list, keys_iterated):
        df = pd.DataFrame(occurence_hits)
        df.rename(columns={df.columns[0]: 'Latency'}, inplace=True)
        for key, val in exp_desc_tuple.items():
            df[key] = val
        raw_hits_dataframe.append(df)

    quantile_data_per_type_and_repetition = []
    for item in raw_hits_dataframe:
        quantile = get_percentiles(item.groupby(['Request_Type', 'Repetition'])['Latency'])
        quantile_data_per_type_and_repetition.append(quantile)

    percentiles = pd.concat(quantile_data_per_type_and_repetition)

    _lineplot(percentiles, exp_name, None, x='Percentile', y='Latency', hue='Request_Type', xlabel='Percentile',
              ylabel='Latency (ms)', huelabel='MultiGET Type', xlim=(0, 1), ylim=(0, None),
              xticks=[0.25, 0.5, 0.75, 0.9, 0.99])

    print(exp_name + " " + req_type[:-2] + ":")
    print(get_average_and_std(percentiles.groupby(['Request_Type', 'Percentile']), 'Latency'))
    print("====================\n\n")


def latency_only(subexperiment, plot=True):
    exp_name = "Experiment {}.{}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
    flattened = memtier_experiment(subexperiment, histogram=False)

    if subexperiment['subexperiment_id'] == 2:
        req_types = 'Non-sharded MultiGET'
    else:
        req_types = 'Sharded MultiGET'

    get_group = flattened[1].groupby(['Type', 'Repetition', 'Worker_Threads'])

    summed_get_throughput = get_sum(get_group, 'Request_Throughput')
    average_get_latency = get_average(get_group, 'Latency')

    concatenated_throughput = pd.concat([summed_get_throughput.assign(RequestType='GET')])
    concatenated_latency = pd.concat([average_get_latency.assign(RequestType='GET')])

    if plot:
        _lineplot(concatenated_throughput, exp_name, None,
                  x='Type', y='Request_Throughput',
                  xlabel=req_types, ylabel='Throughput (req/s)',
                  xlim=(None, None), ylim=(0, 4000), xticks=(np.arange(4), [1, 3, 6, 9]))

        _lineplot(concatenated_latency, exp_name, None, x='Type', y='Latency',
                  xlabel=req_types, ylabel='Latency (ms)',
                  xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

    plotted_throughput_get = summed_get_throughput.groupby(['Type'])
    plotted_latency_get = average_get_latency.groupby(['Type'])

    throughput_flattened_get = get_average_and_std(plotted_throughput_get, 'Request_Throughput')
    latency_flattened_get = get_average_and_std(plotted_latency_get, 'Latency')

    print(exp_name + " GET:")
    print(throughput_flattened_get)
    print("====================\n\n")

    print(exp_name + " SET:")
    print(latency_flattened_get)
    print("====================\n\n")


def throughput_latency_get_set_per_run(subexperiment, plot=True):
    exp_name = "Experiment {}.{}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
    flattened = memtier_experiment(subexperiment)

    set_group = flattened[0].groupby(['Num_Clients', 'Worker_Threads', 'Repetition'])
    get_group = flattened[1].groupby(['Num_Clients', 'Worker_Threads', 'Repetition'])



    print(exp_name + " SET:")
    print(get_sum(set_group, 'Request_Throughput'))
    print("====================\n")

    print(exp_name + " GET:")
    print(get_sum(get_group, 'Request_Throughput'))
    print("====================\n")

    print(exp_name + " SET:")
    print(get_average(set_group, 'Latency'))
    print("====================\n")

    print(exp_name + " GET:")
    print(get_average(get_group, 'Latency'))
    print("====================\n")



def experiment_2():
    throughput_latency_get_set(ed.ExperimentDefinitions.subexpriment_21())
    throughput_latency_get_set(ed.ExperimentDefinitions.subexpriment_22())


def experiment_3():
    throughput_latency_get_set(ed.ExperimentDefinitions.subexpriment_31())
    throughput_latency_get_set(ed.ExperimentDefinitions.subexpriment_32())


def experiment_4():
    throughput_latency_single_request_type(ed.ExperimentDefinitions.subexpriment_40())


def experiment_5():
    histogram_percentiles(ed.ExperimentDefinitions.subexpriment_51())
    latency_only(ed.ExperimentDefinitions.subexpriment_51())
    histogram_percentiles(ed.ExperimentDefinitions.subexpriment_52())
    latency_only(ed.ExperimentDefinitions.subexpriment_52())

def experiment_6():
    print("1 Middleware, 1 Server:")
    throughput_latency_get_set_per_run(ed.ExperimentDefinitions.subexpriment_60_1_1(), plot=False)
    print("\n\n\n1 Middleware, 3 Servers:")
    throughput_latency_get_set_per_run(ed.ExperimentDefinitions.subexpriment_60_1_3(), plot=False)
    print("\n\n\n2 Middlewares, 1 Server:")
    throughput_latency_get_set_per_run(ed.ExperimentDefinitions.subexpriment_60_2_1(), plot=False)
    print("\n\n\n2 Middlewares, 3 Servers:")
    throughput_latency_get_set_per_run(ed.ExperimentDefinitions.subexpriment_60_2_3(), plot=False)



if __name__ == '__main__':
    sns.set(style="ticks", color_codes=True, context="paper")
    pd.set_option("display.width", 250)
    pd.set_option("display.max_rows", 100)
    pd.set_option("display.max_columns", 64)

    experiment_2()
    experiment_3()
    experiment_4()
    experiment_5()
    experiment_6()
