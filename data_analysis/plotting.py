import math
import os
import pathlib
from functools import reduce

import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import numpy as np
import scipy.stats as stats

from experiment_definitions import ExperimentDefinitions
from data_collectors import MemtierCollector, MiddlewareCollector


class PlottingFunctions:

    @staticmethod
    def lineplot(dataframe, experiment_title, save_as_filename,
                 x=None, y=None, hue=None, style=None, ci='sd', err_style='band',
                 xlabel=None, ylabel=None, huelabel=None, stylelabel=None,
                 xlim=(0, None), ylim=(0, None),
                 xticks=None):
        sns.lineplot(x, y, data=dataframe, legend="full", hue=hue, style=style,
                     ci=ci, err_style='band').set(xlabel=xlabel, ylabel=ylabel,
                                                  title=experiment_title,
                                                  xlim=xlim, ylim=ylim)
        sns.scatterplot(x, y, data=dataframe, legend="full", hue=hue, style=style,
                        ci=None).set(xlabel=xlabel, ylabel=ylabel,
                                     title=experiment_title,
                                     xlim=xlim, ylim=ylim)
        if isinstance(xticks, tuple):
            plt.xticks(xticks[0], xticks[1], rotation=45)
        else:
            if xticks[0] == 6 or xticks[0] == 2:
                np.insert(xticks, 0, 0)
            plt.xticks(xticks, rotation=45)
        if huelabel is not None or stylelabel is not None:
            legend = plt.legend(bbox_to_anchor=(1, 1), loc='upper left')
            for txt in legend.get_texts():
                if txt.get_text() is hue and huelabel is not None:
                    txt.set_text(huelabel)
                    continue
                if txt.get_text() is style and stylelabel is not None:
                    txt.set_text(stylelabel)
                    continue

        if save_as_filename is None:
            plt.show()
        else:
            ExperimentPlotter.save_figure(save_as_filename)

    @staticmethod
    def distplot(histogram, experiment_title, save_as_filename,
                 bins=200, kde=False,
                 xlabel=None, ylabel=None, xlim=(0, None), ylim=(0, None),
                 xticks=None):
        sns.distplot(histogram, bins=bins, kde=kde).set(xlabel=xlabel, ylabel=ylabel,
                                                        title=experiment_title,
                                                        xlim=xlim, ylim=ylim)
        if xticks is not None:
            plt.xticks(*xticks)
        if save_as_filename is None:
            plt.show()
        else:
            ExperimentPlotter.save_figure(save_as_filename)

    @staticmethod
    def resplot(dataframe, experiment_title, save_as_filename,
                x=None, y=None,
                xlabel=None, ylabel=None):
        sns.residplot(x, y, dataframe).set(xlabel=xlabel, ylabel=ylabel, title=experiment_title)
        if save_as_filename is None:
            plt.show()
        else:
            ExperimentPlotter.save_figure(save_as_filename)

    @staticmethod
    def qqplot(dataframe, experiment_title, save_as_filename,
               x=None, fit_line=False):
        stats.probplot(dataframe[x], dist="norm", fit=fit_line, plot=plt)
        plt.title(experiment_title)
        if save_as_filename is None:
            plt.show()
        else:
            ExperimentPlotter.save_figure(save_as_filename)

    @staticmethod
    def plot_throughput_by_type(dataframe, experiment_title, save_as_filename,
                                x='Num_Clients', y='Request_Throughput', hue='RequestType', style='Worker_Threads',
                                ci='sd',
                                err_style='bars',
                                xlabel='Memtier Client Count', ylabel='Throughput (req/s)', huelabel='Request Type',
                                stylelabel='Worker Threads',
                                xlim=(0, None), ylim=(0, None),
                                xticks=None):
        if xticks is None:
            xticks = dataframe[x].unique()
        PlottingFunctions.lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style,
                                   xlabel, ylabel, huelabel, stylelabel, xlim, ylim, xticks)

    @staticmethod
    def plot_throughput_family(dataframe, experiment_title, save_as_filename,
                               x='Num_Clients', y='Request_Throughput', hue='Worker_Threads', style=None,
                               ci='sd', err_style='bars',
                               xlabel='Memtier Client Count', ylabel='Throughput (req/s)', huelabel='Worker Threads',
                               stylelabel=None,
                               xlim=(0, None), ylim=(0, None),
                               xticks=None):
        if xticks is None:
            xticks = dataframe[x].unique()
        PlottingFunctions.lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style,
                                   xlabel, ylabel, huelabel, stylelabel, xlim, ylim, xticks)

    @staticmethod
    def plot_response_time_by_type(dataframe, experiment_title, save_as_filename,
                                   x='Num_Clients', y='Response_Time', hue='RequestType', style='Worker_Threads',
                                   ci='sd',
                                   err_style='bars',
                                   xlabel='Memtier Client Count', ylabel='Response Time (ms)', huelabel='Request Type',
                                   stylelabel='Worker Threads',
                                   xlim=(0, None), ylim=(0, None),
                                   xticks=None):
        if xticks is None:
            xticks = dataframe[x].unique()
        PlottingFunctions.lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style,
                                   xlabel, ylabel, huelabel, stylelabel, xlim, ylim, xticks)

    @staticmethod
    def plot_response_time_family(dataframe, experiment_title, save_as_filename,
                                  x='Num_Clients', y='Response_Time', hue='Worker_Threads', style=None, ci='sd',
                                  err_style='bars',
                                  xlabel='Memtier Client Count', ylabel='Response Time (ms)', huelabel='Worker Threads',
                                  stylelabel=None,
                                  xlim=(0, None), ylim=(0, None),
                                  xticks=None):
        if xticks is None:
            xticks = dataframe[x].unique()
        PlottingFunctions.lineplot(dataframe, experiment_title, save_as_filename, x, y, hue, style, ci, err_style,
                                   xlabel, ylabel, huelabel, stylelabel, xlim, ylim, xticks)

    @staticmethod
    def plot_histogram(histogram, experiment_title, save_as_filename, bins=200, kde=False,
                       xlabel='Buckets (ms)', ylabel='Request Count', xlim=(0, 20), ylim=(0, 8000),
                       xticks=None):
        if xticks is None:
            xticks = (np.arange(0, (bins / 10) + 0.1, step=2.5), np.linspace(0, bins / 10, 9))
        PlottingFunctions.distplot(histogram, experiment_title, save_as_filename, bins, kde,
                                   xlabel, ylabel, xlim, ylim, xticks)


class StatisticsFunctions:
    @staticmethod
    def get_average_and_std(dataframe, aggregate_on):
        return dataframe[aggregate_on].agg(['mean', 'std']).reset_index().rename(index=str,
                                                                                 columns={
                                                                                     "mean": aggregate_on + '_Mean',
                                                                                     "std": aggregate_on + '_Std'})

    @staticmethod
    def get_sum(dataframe, aggregate_on):
        return dataframe[aggregate_on].agg(['sum']).reset_index().rename(index=str, columns={"sum": aggregate_on})

    @staticmethod
    def get_average(dataframe, aggregate_on):
        return dataframe[aggregate_on].agg(['mean']).reset_index().rename(index=str, columns={"mean": aggregate_on})

    @staticmethod
    def get_percentiles(dataframe):
        return dataframe.quantile(([.25, .50, .75, .90, .99])).reset_index().rename(index=str,
                                                                                    columns={"level_2": 'Percentile'})

    @staticmethod
    def mm1(summary_table, plot=False):
        calculations = []
        for row in summary_table.itertuples():
            lamb = row[4]
            muh = row[-1]
            measured_response_time = row[6]
            measured_queue_waiting_time = row[8]
            measured_queue_size = row[12]

            traffic_intensity = lamb / muh
            mean_nr_jobs_in_system = traffic_intensity / (1 - traffic_intensity)
            mean_nr_jobs_in_queue = traffic_intensity * mean_nr_jobs_in_system
            mean_response_time = (1 / muh) / (1 - traffic_intensity)
            mean_waiting_time = traffic_intensity * mean_response_time

            calculations.append({'Num_Clients': row[1],
                                 'Worker_Threads': row[2],
                                 'Maximum_Service_Rate': muh,
                                 'Arrival_Rate': lamb,
                                 'Traffic_Intensity': traffic_intensity,
                                 'Mean_Number_Jobs_System': mean_nr_jobs_in_system,
                                 'Measured_Response_Time': measured_response_time,
                                 'Estimated_Response_Time': mean_response_time * 1000,
                                 'Measured_Queue_Waiting_Time': measured_queue_waiting_time,
                                 'Estimated_Queue_Waiting_Time': mean_waiting_time * 1000,
                                 'Measured_Queue_Size': measured_queue_size,
                                 'Estimated_Queue_Size': mean_nr_jobs_in_queue})

        mm1_analysis = pd.DataFrame(calculations)
        mm1_analysis = mm1_analysis[['Num_Clients', 'Worker_Threads', 'Maximum_Service_Rate', 'Arrival_Rate',
                                     'Traffic_Intensity', 'Mean_Number_Jobs_System', 'Measured_Response_Time',
                                     'Estimated_Response_Time', 'Measured_Queue_Waiting_Time',
                                     'Estimated_Queue_Waiting_Time', 'Measured_Queue_Size', 'Estimated_Queue_Size']]
        return mm1_analysis

    @staticmethod
    def mmm(summary_table, plot=False):
        calculations = []
        for row in summary_table.itertuples():
            lamb = row[4]
            servers = row[2] * 2
            muh = row[-1] / servers
            measured_response_time = row[6]
            measured_queue_waiting_time = row[8]
            measured_queue_size = row[12]

            traffic_intensity = lamb / (muh * servers)
            _param1 = math.pow(servers * traffic_intensity, servers) / (math.factorial(servers) * (1 - traffic_intensity))
            probability_zero_jobs_in_system = 1 / (1 + _param1 +
                                                   sum([pow(servers * traffic_intensity, n) / math.factorial(n) for n in
                                                        range(1, servers)]))
            probability_of_queueing = probability_zero_jobs_in_system * _param1
            mean_number_jobs_in_queue = (traffic_intensity * probability_of_queueing) / (1 - traffic_intensity)
            mean_number_jobs_in_system = servers * traffic_intensity + mean_number_jobs_in_queue
            average_utilization_each_server = traffic_intensity
            mean_response_time = (1 / muh) * (1 + probability_of_queueing / (servers * (1 - traffic_intensity)))
            mean_waiting_time = mean_number_jobs_in_queue / lamb

            calculations.append({'Num_Clients': row[1],
                                 'Worker_Threads': row[2],
                                 'Maximum_Service_Rate': muh,
                                 'Arrival_Rate': lamb,
                                 'Traffic_Intensity': traffic_intensity,
                                 'Mean_Number_Jobs_System': mean_number_jobs_in_system,
                                 'Measured_Response_Time': measured_response_time,
                                 'Estimated_Response_Time': mean_response_time * 1000,
                                 'Measured_Queue_Waiting_Time': measured_queue_waiting_time,
                                 'Estimated_Queue_Waiting_Time': mean_waiting_time * 1000,
                                 'Measured_Queue_Size': measured_queue_size,
                                 'Estimated_Queue_Size': mean_number_jobs_in_queue,
                                 'Probability_Zero_Jobs_System': probability_zero_jobs_in_system,
                                 'Probability_Queueing': probability_of_queueing,
                                 'Mean_Average_Utilization_Each_Server': average_utilization_each_server})

        mmm_analysis = pd.DataFrame(calculations)
        mmm_analysis = mmm_analysis[['Num_Clients', 'Worker_Threads', 'Maximum_Service_Rate', 'Arrival_Rate',
                                     'Traffic_Intensity', 'Mean_Number_Jobs_System', 'Measured_Response_Time',
                                     'Estimated_Response_Time', 'Measured_Queue_Waiting_Time',
                                     'Estimated_Queue_Waiting_Time', 'Measured_Queue_Size', 'Estimated_Queue_Size',
                                     'Probability_Zero_Jobs_System', 'Probability_Queueing',
                                     'Mean_Average_Utilization_Each_Server']]

        return mmm_analysis


class ExperimentPlotter:

    @staticmethod
    def save_figure(save_as_filename):
        current_dir = pathlib.Path(__file__).parent
        figure_path = current_dir.joinpath("figures")
        if not os.path.exists(figure_path):
            os.makedirs(figure_path)
        figure_path = figure_path.joinpath(save_as_filename + ".png")
        plt.savefig(figure_path, dpi=150, bbox_inches='tight')
        plt.close()

    @staticmethod
    def memtier_experiment(experiment_definition, histogram=False):
        memtier_collector = MemtierCollector(experiment_definition)
        memtier_collector.generate_dataframe(histogram)
        return [[memtier_collector.dataframe_set, memtier_collector.dataframe_get],
                [memtier_collector.dataframe_histogram_set, memtier_collector.dataframe_histogram_get]]

    @staticmethod
    def middleware_experiment(experiment_definition, histogram=False):
        middleware_collector = MiddlewareCollector(experiment_definition)
        middleware_collector.generate_dataframe(histogram)
        return [[middleware_collector.dataframe_set, middleware_collector.dataframe_get],
                [middleware_collector.dataframe_histogram_set, middleware_collector.dataframe_histogram_get]]

    @staticmethod
    def memtier_statistics_get_set(flattened, subexperiment, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Memtier')
        set_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])
        get_group = flattened[1].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        throughput_set = StatisticsFunctions.get_sum(set_group, 'Request_Throughput')
        throughput_get = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
        response_time_set = StatisticsFunctions.get_average(set_group, 'Response_Time')
        response_time_get = StatisticsFunctions.get_average(get_group, 'Response_Time')
        hits_get = StatisticsFunctions.get_sum(get_group, 'Hits')
        misses_get = StatisticsFunctions.get_sum(get_group, 'Misses')

        if plot:
            concatenated_throughput = pd.concat([throughput_set.assign(RequestType='SET'),
                                                 throughput_get.assign(RequestType='GET')])
            concatenated_response_time = pd.concat([response_time_set.assign(RequestType='SET'),
                                                    response_time_get.assign(RequestType='GET')])

            throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
            throughput_interactive = concatenated_throughput[
                concatenated_throughput.Type.str.contains('Interactive')]

            response_time_measured = concatenated_response_time[
                ~concatenated_response_time.Type.str.contains('Interactive')]
            response_time_interactive = concatenated_response_time[
                concatenated_response_time.Type.str.contains('Interactive')]

            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, plot_base + 'mt_throughput')
            PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name,
                                                         plot_base + 'mt_response_time')

            PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law',
                                                      plot_base + 'mt_throughput-il')
            PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law',
                                                         plot_base + 'mt_response-time-il')

        hits_get = StatisticsFunctions.get_average_and_std(
            hits_get.groupby(['Num_Clients', 'Worker_Threads', 'Type']), 'Hits')
        misses_get = StatisticsFunctions.get_average_and_std(
            misses_get.groupby(['Num_Clients', 'Worker_Threads', 'Type']), 'Misses')

        plotted_throughput_set = throughput_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_throughput_get = throughput_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_set = response_time_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_get = response_time_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

        throughput_set_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_set,
                                                                         'Request_Throughput')
        throughput_get_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_get,
                                                                         'Request_Throughput')
        response_time_set_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_set,
                                                                            'Response_Time')
        response_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_get,
                                                                            'Response_Time')

        set_table_list = [throughput_set_plotted, response_time_set_plotted]
        get_table_list = [throughput_get_plotted, response_time_get_plotted, misses_get, hits_get]

        set_summary = reduce(lambda left, right: pd.merge(left, right,
                                                          on=['Num_Clients', 'Worker_Threads', 'Type']),
                             set_table_list)

        get_summary = reduce(lambda left, right: pd.merge(left, right,
                                                          on=['Num_Clients', 'Worker_Threads', 'Type']),
                             get_table_list)

        print(exp_name + " SET:")
        print(set_summary)
        print("====================\n")

        print(exp_name + " GET:")
        print(get_summary)
        print("====================\n")
        return [set_summary, get_summary]

    @staticmethod
    def memtier_statistics_request_family(flattened, subexperiment, r_type='SET', plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Memtier')
        family = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        throughput_family = StatisticsFunctions.get_sum(family, 'Request_Throughput')
        response_time_family = StatisticsFunctions.get_average(family, 'Response_Time')

        if plot:
            concatenated_throughput = pd.concat([throughput_family.assign(RequestType=r_type)])
            concatenated_response_time = pd.concat([response_time_family.assign(RequestType=r_type)])

            throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
            throughput_interactive = concatenated_throughput[concatenated_throughput.Type.str.contains('Interactive')]

            response_time_measured = concatenated_response_time[
                ~concatenated_response_time.Type.str.contains('Interactive')]
            response_time_interactive = concatenated_response_time[
                concatenated_response_time.Type.str.contains('Interactive')]

            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, plot_base + 'mt_throughput')
            PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name,
                                                         plot_base + 'mt_response_time')

            PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law',
                                                      plot_base + 'mt_throughput-il')
            PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law',
                                                         plot_base + 'mt_response-time-il')

        plotted_throughput_family = throughput_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_family = response_time_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

        throughput_family_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_family,
                                                                            'Request_Throughput')
        response_time_family_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_family,
                                                                               'Response_Time')

        family_table_list = [throughput_family_plotted, response_time_family_plotted]

        family_summary = reduce(lambda left, right: pd.merge(left, right,
                                                             on=['Num_Clients', 'Worker_Threads', 'Type']),
                                family_table_list)

        print(exp_name + " " + r_type + ":")
        print(family_summary)
        print("====================\n")
        return family_summary

    @staticmethod
    def memtier_statistics_multiget(flattened, subexperiment, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Memtier')

        if subexperiment['subexperiment_id'] == 2:
            req_types = 'Non-sharded MultiGET'
        else:
            req_types = 'Sharded MultiGET'

        get_group = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        get_group = get_group.groupby(['Type', 'Repetition', 'Worker_Threads'])

        summed_get_throughput = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
        average_get_response_time = StatisticsFunctions.get_average(get_group, 'Response_Time')
        hits_get = StatisticsFunctions.get_sum(get_group, 'Hits')
        misses_get = StatisticsFunctions.get_sum(get_group, 'Misses')

        concatenated_throughput = pd.concat([summed_get_throughput.assign(RequestType='GET')])
        concatenated_response_time = pd.concat([average_get_response_time.assign(RequestType='GET')])

        if plot:
            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.lineplot(concatenated_throughput, exp_name, plot_base + 'mt_throughput', x='Type', y='Request_Throughput',
                                       xlabel=req_types, ylabel='Throughput (req/s)',
                                       xlim=(None, None), ylim=(0, 4000), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_response_time, exp_name, plot_base + 'mt_response-time', x='Type', y='Response_Time',
                                       xlabel=req_types, ylabel='Response Time (ms)',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

        hits_get = StatisticsFunctions.get_average_and_std(hits_get.groupby(['Type']), 'Hits')
        misses_get = StatisticsFunctions.get_average_and_std(misses_get.groupby(['Type']), 'Misses')

        plotted_throughput_get = summed_get_throughput.groupby(['Type'])
        plotted_response_time_get = average_get_response_time.groupby(['Type'])

        throughput_get_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_get, 'Request_Throughput')
        response_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_get,
                                                                            'Response_Time')

        get_table_list = [throughput_get_plotted, response_time_get_plotted, misses_get, hits_get]

        get_summary = reduce(lambda left, right: pd.merge(left, right, on=['Type']), get_table_list)

        print(exp_name + " GET:")
        print(get_summary)
        print("====================\n\n")
        return get_summary

    @staticmethod
    def middleware_statistics_get_set(flattened, subexperiment, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Middleware')

        set_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])
        get_group = flattened[1].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        # throughput_hosts_set = flattened[0].groupby(['NumClients', 'Repetition', 'Worker_Threads', 'Type', 'Host'])
        # host_factors_set = StatisticsFunctions.get_sum(throughput_hosts_set, 'Request_Throughput')
        #
        # throughput_hosts_get = flattened[1].groupby(['NumClients', 'Repetition', 'Worker_Threads', 'Type', 'Host'])
        # host_factors_get = StatisticsFunctions.get_sum(throughput_hosts_get, 'Request_Throughput')


        throughput_set = StatisticsFunctions.get_sum(set_group, 'Request_Throughput')
        throughput_get = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
        response_time_set = StatisticsFunctions.get_average(set_group, 'Response_Time')
        response_time_get = StatisticsFunctions.get_average(get_group, 'Response_Time')

        set_group = flattened[0][~flattened[0].Type.str.contains('Interactive')]
        set_group = set_group.groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])
        get_group = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        get_group = get_group.groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        queue_waiting_time_set = StatisticsFunctions.get_average(set_group, 'Queue_Waiting_Time')
        queue_waiting_time_get = StatisticsFunctions.get_average(get_group, 'Queue_Waiting_Time')
        memcached_communication_set = StatisticsFunctions.get_average(set_group, 'Memcached_Communication')
        memcached_communication_get = StatisticsFunctions.get_average(get_group, 'Memcached_Communication')
        queue_size_set = StatisticsFunctions.get_average(set_group, 'Queue_Size')
        queue_size_get = StatisticsFunctions.get_average(get_group, 'Queue_Size')
        hits_get = StatisticsFunctions.get_sum(get_group, 'Hits')
        misses_get = StatisticsFunctions.get_sum(get_group, 'Misses')

        if plot:
            xticks = flattened[0]['Num_Clients'].unique()

            concatenated_throughput = pd.concat([throughput_set.assign(RequestType='SET'),
                                                 throughput_get.assign(RequestType='GET')])
            concatenated_response_time = pd.concat([response_time_set.assign(RequestType='SET'),
                                                    response_time_get.assign(RequestType='GET')])
            concatenated_queue_waiting_time = pd.concat([queue_waiting_time_set.assign(RequestType='SET'),
                                                         queue_waiting_time_get.assign(RequestType='GET')])
            concatenated_memcached_communication = pd.concat([memcached_communication_set.assign(RequestType='SET'),
                                                              memcached_communication_get.assign(RequestType='GET')])
            concatenated_queue_size = pd.concat([queue_size_set.assign(RequestType='SET'),
                                                 queue_size_get.assign(RequestType='GET')])

            throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
            throughput_interactive = concatenated_throughput[
                concatenated_throughput.Type.str.contains('Interactive')]

            response_time_measured = concatenated_response_time[
                ~concatenated_response_time.Type.str.contains('Interactive')]
            response_time_interactive = concatenated_response_time[
                concatenated_response_time.Type.str.contains('Interactive')]

            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, plot_base + 'mw_throughput')
            PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name,
                                                         plot_base + 'mw_response_time')

            PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law',
                                                      plot_base + 'mw_throughput-il')
            PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law',
                                                         plot_base + 'mw_response-time-il')

            PlottingFunctions.lineplot(concatenated_queue_waiting_time, exp_name, plot_base + "mw_queue-wait-time",
                                       x='Num_Clients', y='Queue_Waiting_Time', hue='RequestType',
                                       style='Worker_Threads', xlabel='Number Memtier Clients',
                                       ylabel='Queue Waiting Time (ms)', huelabel='Request Type',
                                       stylelabel='Worker Threads', xlim=(0, None), ylim=(0, None), xticks=xticks)
            PlottingFunctions.lineplot(concatenated_memcached_communication, exp_name, plot_base + "mw_mc-comm-time",
                                       x='Num_Clients', y='Memcached_Communication', hue='RequestType',
                                       style='Worker_Threads', xlabel='Number Memtier Clients',
                                       ylabel='Memcached Handling (ms)',
                                       huelabel='Request Type', stylelabel='Worker Threads',
                                       xlim=(0, None), ylim=(0, None), xticks=xticks)
            PlottingFunctions.lineplot(concatenated_queue_size, exp_name, plot_base + "mw_queue-size", x='Num_Clients',
                                       y='Queue_Size', hue='RequestType', style='Worker_Threads',
                                       xlabel='Number Memtier Clients', ylabel='Queue Size',
                                       huelabel='Request Type', stylelabel='Worker Threads',
                                       xlim=(0, None), ylim=(0, None), xticks=xticks)

        hits_get = StatisticsFunctions.get_average_and_std(hits_get.groupby(['Num_Clients', 'Worker_Threads', 'Type']),
                                                           'Hits')
        misses_get = StatisticsFunctions.get_average_and_std(
            misses_get.groupby(['Num_Clients', 'Worker_Threads', 'Type']), 'Misses')

        plotted_throughput_set = throughput_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_throughput_get = throughput_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_set = response_time_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_get = response_time_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_waiting_time_set = queue_waiting_time_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_waiting_time_get = queue_waiting_time_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_memcached_communication_set = memcached_communication_set.groupby(
            ['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_memcached_communication_get = memcached_communication_get.groupby(
            ['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_size_set = queue_size_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_size_get = queue_size_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

        throughput_set_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_set,
                                                                         'Request_Throughput')
        throughput_get_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_get,
                                                                         'Request_Throughput')
        response_time_set_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_set,
                                                                            'Response_Time')
        response_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_get,
                                                                            'Response_Time')
        queue_waiting_time_set_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_waiting_time_set,
                                                                                 'Queue_Waiting_Time')
        queue_waiting_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_waiting_time_get,
                                                                                 'Queue_Waiting_Time')
        memcached_communication_set_plotted = StatisticsFunctions.get_average_and_std(
            plotted_memcached_communication_set, 'Memcached_Communication')
        memcached_communication_get_plotted = StatisticsFunctions.get_average_and_std(
            plotted_memcached_communication_get, 'Memcached_Communication')
        queue_size_set_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_size_set, 'Queue_Size')
        queue_size_get_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_size_get, 'Queue_Size')

        set_table_list = [throughput_set_plotted, response_time_set_plotted, queue_waiting_time_set_plotted,
                          memcached_communication_set_plotted, queue_size_set_plotted]
        get_table_list = [throughput_get_plotted, response_time_get_plotted, queue_waiting_time_get_plotted,
                          memcached_communication_get_plotted, queue_size_get_plotted, misses_get, hits_get]

        set_summary = reduce(lambda left, right: pd.merge(left, right,
                                                          on=['Num_Clients', 'Worker_Threads', 'Type']),
                             set_table_list)

        get_summary = reduce(lambda left, right: pd.merge(left, right,
                                                          on=['Num_Clients', 'Worker_Threads', 'Type']),
                             get_table_list)

        print(exp_name + " SET:")
        print(set_summary)
        print("====================\n")

        print(exp_name + " GET:")
        print(get_summary)
        print("====================\n")
        return [set_summary, get_summary]

    @staticmethod
    def middleware_statistics_request_family(flattened, subexperiment, r_type='SET', plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Middleware')

        family = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        throughput_family = StatisticsFunctions.get_sum(family, 'Request_Throughput')
        response_time_family = StatisticsFunctions.get_average(family, 'Response_Time')

        family = flattened[0][~flattened[0].Type.str.contains('Interactive')]
        family = family.groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        queue_waiting_time_family = StatisticsFunctions.get_average(family, 'Queue_Waiting_Time')
        memcached_communication_family = StatisticsFunctions.get_average(family, 'Memcached_Communication')
        queue_size_family = StatisticsFunctions.get_average(family, 'Queue_Size')

        if plot:
            xticks = flattened[0]['Num_Clients'].unique()

            concatenated_throughput = pd.concat([throughput_family.assign(RequestType=r_type)])
            concatenated_response_time = pd.concat([response_time_family.assign(RequestType=r_type)])
            concatenated_queue_waiting_time = pd.concat([queue_waiting_time_family.assign(RequestType=r_type)])
            concatenated_memcached_communication = pd.concat(
                [memcached_communication_family.assign(RequestType=r_type)])
            concatenated_queue_size = pd.concat([queue_size_family.assign(RequestType=r_type)])

            throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
            throughput_interactive = concatenated_throughput[concatenated_throughput.Type.str.contains('Interactive')]

            response_time_measured = concatenated_response_time[
                ~concatenated_response_time.Type.str.contains('Interactive')]
            response_time_interactive = concatenated_response_time[
                concatenated_response_time.Type.str.contains('Interactive')]

            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, plot_base + 'mw_throughput')
            PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name,
                                                         plot_base + 'mw_response_time')

            PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law',
                                                      plot_base + 'mw_throughput-il')
            PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law',
                                                         plot_base + 'mw_response-time-il')

            PlottingFunctions.lineplot(concatenated_queue_waiting_time, exp_name, plot_base + "mw_queue-wait-time",
                                       x='Num_Clients', y='Queue_Waiting_Time', hue='Worker_Threads',
                                       xlabel='Number Memtier Clients', ylabel='Queue Waiting Time (ms)',
                                       huelabel='Worker Threads',xlim=(0, None), ylim=(0, None), xticks=xticks)
            PlottingFunctions.lineplot(concatenated_memcached_communication, exp_name, plot_base + "mw_mc-comm-time",
                                       x='Num_Clients', y='Memcached_Communication', hue='Worker_Threads',
                                       xlabel='Number Memtier Clients',
                                       ylabel='Memcached Handling (ms)',
                                       huelabel='Worker Threads', xlim=(0, None), ylim=(0, None), xticks=xticks)
            PlottingFunctions.lineplot(concatenated_queue_size, exp_name, plot_base + "mw_queue-size", x='Num_Clients',
                                       y='Queue_Size', hue='Worker_Threads', xlabel='Number Memtier Clients',
                                       ylabel='Queue Size', huelabel='Worker Threads', xlim=(0, None), ylim=(0, None),
                                       xticks=xticks)

        plotted_throughput_family = throughput_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_family = response_time_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_waiting_time_family = queue_waiting_time_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_memcached_communication_family = memcached_communication_family.groupby(
            ['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_queue_size_family = queue_size_family.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

        throughput_family_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_family,
                                                                            'Request_Throughput')
        response_time_family_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_family,
                                                                               'Response_Time')
        queue_waiting_time_family_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_waiting_time_family,
                                                                                    'Queue_Waiting_Time')
        memcached_communication_family_plotted = StatisticsFunctions.get_average_and_std(
            plotted_memcached_communication_family, 'Memcached_Communication')
        queue_size_family_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_size_family, 'Queue_Size')

        family_table_list = [throughput_family_plotted, response_time_family_plotted, queue_waiting_time_family_plotted,
                             memcached_communication_family_plotted, queue_size_family_plotted]

        family_summary = reduce(lambda left, right: pd.merge(left, right,
                                                             on=['Num_Clients', 'Worker_Threads', 'Type'], how='outer'),
                                family_table_list)

        print(exp_name + " " + r_type + ":")
        print(family_summary)
        print("====================\n")
        return family_summary

    @staticmethod
    def middleware_statistics_multiget(flattened, subexperiment, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Middleware')

        if subexperiment['subexperiment_id'] == 2:
            req_types = 'Non-sharded MultiGET'
        else:
            req_types = 'Sharded MultiGET'

        get_group = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        get_group = get_group.groupby(['Type', 'Repetition'])

        summed_get_throughput = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
        average_get_response_time = StatisticsFunctions.get_average(get_group, 'Response_Time')
        queue_waiting_time_set = StatisticsFunctions.get_average(get_group, 'Queue_Waiting_Time')
        memcached_communication_set = StatisticsFunctions.get_average(get_group, 'Memcached_Communication')
        queue_size_set = StatisticsFunctions.get_average(get_group, 'Queue_Size')
        hits_get = StatisticsFunctions.get_sum(get_group, 'Hits')
        misses_get = StatisticsFunctions.get_sum(get_group, 'Misses')
        keysize_get = StatisticsFunctions.get_sum(get_group, 'Request_Size')
        key_throughput_get = StatisticsFunctions.get_sum(get_group, 'Key_Throughput')

        if plot:
            concatenated_throughput = pd.concat([summed_get_throughput.assign(RequestType='GET')])
            concatenated_response_time = pd.concat([average_get_response_time.assign(RequestType='GET')])
            concatenated_queue_waiting_time = pd.concat([queue_waiting_time_set.assign(RequestType='GET')])
            concatenated_memcached_communication = pd.concat([memcached_communication_set.assign(RequestType='GET')])
            concatenated_queue_size = pd.concat([queue_size_set.assign(RequestType='GET')])

            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])

            PlottingFunctions.lineplot(concatenated_throughput, exp_name, plot_base + "mw_thoughput", x='Type', y='Request_Throughput',
                                       xlabel=req_types, ylabel='Throughput (req/s)',
                                       xlim=(None, None), ylim=(0, 4000), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_response_time, exp_name, plot_base + "mw_response-time", x='Type', y='Response_Time',
                                       xlabel=req_types, ylabel='Response Time (ms)',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_queue_waiting_time, exp_name, plot_base + "mw_queue-wait-time", x='Type',
                                       y='Queue_Waiting_Time',
                                       xlabel=req_types, ylabel='Queue Waiting Time (ms)',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_memcached_communication, exp_name, plot_base + "mw_mc-comm-time", x='Type',
                                       y='Memcached_Communication',
                                       xlabel=req_types, ylabel='Memcached Communication and Packet Handling (ms)',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_queue_size, exp_name, plot_base + "mw_queue-size", x='Type',
                                       y='Queue_Size',
                                       xlabel=req_types, ylabel='Queue Size',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

        hits_get = StatisticsFunctions.get_average_and_std(hits_get.groupby(['Type']), 'Hits')
        misses_get = StatisticsFunctions.get_average_and_std(misses_get.groupby(['Type']), 'Misses')
        keysize_get = StatisticsFunctions.get_average_and_std(keysize_get.groupby(['Type']), 'Request_Size')
        key_throughput_get = StatisticsFunctions.get_average_and_std(key_throughput_get.groupby(['Type']),
                                                                     'Key_Throughput')

        plotted_throughput_get = summed_get_throughput.groupby(['Type'])
        plotted_response_time_get = average_get_response_time.groupby(['Type'])
        plotted_queue_waiting_time_get = queue_waiting_time_set.groupby(['Type'])
        plotted_memcached_communication_get = memcached_communication_set.groupby(['Type'])
        plotted_queue_size_get = queue_size_set.groupby(['Type'])

        throughput_get_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_get, 'Request_Throughput')
        response_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_get,
                                                                            'Response_Time')
        queue_waiting_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_waiting_time_get,
                                                                                 'Queue_Waiting_Time')
        memcached_communication_get_plotted = StatisticsFunctions.get_average_and_std(
            plotted_memcached_communication_get, 'Memcached_Communication')
        queue_size_get_plotted = StatisticsFunctions.get_average_and_std(plotted_queue_size_get, 'Queue_Size')

        get_table_list = [throughput_get_plotted, response_time_get_plotted, queue_waiting_time_get_plotted,
                          memcached_communication_get_plotted, queue_size_get_plotted, misses_get, hits_get,
                          keysize_get, key_throughput_get]

        get_summary = reduce(lambda left, right: pd.merge(left, right, on=['Type']), get_table_list)

        print(exp_name + " GET:")
        print(get_summary)
        print("====================\n\n")
        return get_summary

    @staticmethod
    def middleware_statistics_mmx(flattened, subexperiment):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  'Middleware')

        family = flattened[0][~flattened[0].Type.str.contains('Interactive')]
        family = family.groupby(['Num_Clients', 'Worker_Threads', 'Repetition'])

        throughput_family = StatisticsFunctions.get_sum(family, 'Request_Throughput')
        response_time_family = StatisticsFunctions.get_average(family, 'Response_Time')
        queue_waiting_time_family = StatisticsFunctions.get_average(family, 'Queue_Waiting_Time')
        memcached_communication_family = StatisticsFunctions.get_average(family, 'Memcached_Communication')
        queue_size_family = StatisticsFunctions.get_average(family, 'Queue_Size')

        merged_table = [throughput_family, response_time_family, memcached_communication_family,
                        queue_waiting_time_family, queue_size_family]

        merged_dataframe = reduce(lambda left, right: pd.merge(left, right,
                                                               on=['Num_Clients', 'Repetition', 'Worker_Threads']),
                                  merged_table)

        maximum_service_rates = merged_dataframe.iloc[
            merged_dataframe.groupby(['Worker_Threads'])['Request_Throughput'].idxmax().values.ravel()]
        maximum_service_rates.rename(index=str, columns={'Request_Throughput': 'Maximum_Service_Rate'}, inplace=True)

        print(exp_name + " SET:")
        print(merged_dataframe)
        print("====================\n")

        print(exp_name + " Maximum Service Rates:")
        print(maximum_service_rates)
        print("====================\n")

        return maximum_service_rates

    @staticmethod
    def histogram_6keys(flattened, subexperiment, measured_from, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)

        if subexperiment['subexperiment_id'] == 2:
            req_type = 'MULTIGET_6'
        else:
            req_type = 'SHARDED_6'

        no_interactive_law = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        summed_get_bucket = (no_interactive_law[no_interactive_law['Type'] == req_type]).groupby(
            ['Bucket', 'Repetition']).agg(
            {'Count': np.sum}).reset_index()
        summed_get_bucket['Bucket_Double'] = summed_get_bucket['Bucket'].str.extract('(\d+.\d)', expand=False).astype(
            np.float64)
        average_buckets = summed_get_bucket.groupby(['Bucket_Double'])['Count'].agg(['mean'])

        occurrence_list = []
        for _, row in average_buckets.iterrows():
            occurences = [row.name] * int(row['mean'])
            if int(row['mean']) > 0:
                occurrence_list.extend(occurences)
            else:
                occurrence_list.append(row.name)  # stability of bucket visualization

        # Will hold an unrolled view of buckets, meaning if a bucket held N times, the bucket's label will be present N
        # times
        histogram = pd.DataFrame(occurrence_list)

        if plot:
            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
            if measured_from == 'Middleware':
                plot_base += "mw_"
            elif measured_from == 'Memtier':
                plot_base += "mt_"
            PlottingFunctions.plot_histogram(histogram, exp_name, plot_base + "histogram")

    @staticmethod
    def percentiles_multiget(flattened, subexperiment, measured_from, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)
        no_interactive_law = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        summed_get_bucket = no_interactive_law.groupby(['Bucket', 'Repetition', 'Type']).agg(
            {'Count': np.sum}).reset_index()

        # Unroll all experiments into a list and remember their ordering in the list (easier background processing)
        histograms = []
        keys_iterated = []
        for req_type in subexperiment['request_types']:
            for rep in range(1, 4):
                histograms.append(summed_get_bucket.loc[(summed_get_bucket['Type'] == req_type) & (
                        summed_get_bucket['Repetition'] == rep)])
                keys_iterated.append({'Request_Type': req_type, 'Repetition': rep})

        # Unroll all bucket hits (bucket had value X --> X repetitions of same value) -- We don't add empty buckets here
        # for stability.
        occurences_list = []
        for item in histograms:
            occurence_list = []
            for _, row in item.iterrows():
                occurences = [float(row['Bucket'])] * int(row['Count'])
                if int(row['Count']) > 0:
                    occurence_list.extend(occurences)
            occurences_list.append(occurence_list)

        # Convert back data into multiple dataframes
        raw_hits_dataframe = []
        for occurence_hits, exp_desc_tuple in zip(occurences_list, keys_iterated):
            df = pd.DataFrame(occurence_hits)
            df.rename(columns={df.columns[0]: 'Response_Time'}, inplace=True)
            for key, val in exp_desc_tuple.items():
                df[key] = val
            raw_hits_dataframe.append(df)

        # Calculate on each dataframe the quantiles
        quantile_data_per_type_and_repetition = []
        for item in raw_hits_dataframe:
            quantile = StatisticsFunctions.get_percentiles(
                item.groupby(['Request_Type', 'Repetition'])['Response_Time'])
            quantile_data_per_type_and_repetition.append(quantile)

        # The actual final dataframe which holds all quantile values
        percentiles = pd.concat(quantile_data_per_type_and_repetition)

        if plot:
            plot_base = "{}-{}_".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'])
            if measured_from == 'Middleware':
                plot_base += "mw_"
            elif measured_from == 'Memtier':
                plot_base += "mt_"
            PlottingFunctions.lineplot(percentiles, exp_name, plot_base + "percentiles", x='Percentile', y='Response_Time',
                                       hue='Request_Type',
                                       xlabel='Percentile', ylabel='Response Time (ms)', huelabel='MultiGET Type',
                                       xlim=(0, 1), ylim=(0, None), xticks=[0.25, 0.5, 0.75, 0.9, 0.99])

        print(exp_name + " GET Percentiles:")
        print(StatisticsFunctions.get_average_and_std(percentiles.groupby(['Request_Type', 'Percentile']),
                                                      'Response_Time'))
        print("====================\n\n")

    @staticmethod
    def exp_6_pretty_table(exp_list, plot=False, multiplicative_model=False):
        exp_tables = []
        exp_tables_readable = []
        for key, value in exp_list:
            mw_count = len(key['memtier_targets'])
            mc_count = len(key['memcached_servers'])
            if multiplicative_model:
                value[0]['Request_Throughput'] = value[0]['Request_Throughput'].apply(lambda x: np.log10(x))
                value[1]['Request_Throughput'] = value[1]['Request_Throughput'].apply(lambda x: np.log10(x))
                value[0]['Response_Time'] = value[0]['Response_Time'].apply(lambda x: np.log10(x))
                value[1]['Response_Time'] = value[1]['Response_Time'].apply(lambda x: np.log10(x))
            sets = value[0][~value[0].Type.str.contains('Interactive')].groupby(['Repetition', 'Worker_Threads',
                                                                                 'Type'])
            gets = value[1][~value[1].Type.str.contains('Interactive')].groupby(['Repetition', 'Worker_Threads',
                                                                                 'Type'])

            data_list = [sets, gets]
            throughputs = []
            response_times = []
            list_throughput = [StatisticsFunctions.get_sum(df, 'Request_Throughput') for df in data_list]
            list_throughput_averages = [StatisticsFunctions.get_average(df.groupby(['Worker_Threads']),
                                                                        'Request_Throughput') for df in list_throughput]
            for run_results, average in zip(list_throughput, list_throughput_averages):
                run_results['Request_Throughput_Residual'] = run_results.Request_Throughput -\
                                                             run_results.Worker_Threads.map(
                                                                 dict(zip(average.Worker_Threads.values,
                                                                          average.Request_Throughput)))
                average.rename(index=str, columns={'Request_Throughput': 'Request_Throughput_Mean'}, inplace=True)
                throughputs.append(pd.DataFrame.merge(run_results, average, on=['Worker_Threads']))
            list_response_time = [StatisticsFunctions.get_average(df, 'Response_Time') for df in data_list]
            list_response_time_averages = [
                StatisticsFunctions.get_average(df.groupby(['Worker_Threads']), 'Response_Time') for df in
                list_response_time]
            for run_results, average in zip(list_response_time, list_response_time_averages):
                run_results['Response_Time_Residual'] = run_results.Response_Time - run_results.Worker_Threads.map(
                    dict(zip(average.Worker_Threads.values, average.Response_Time)))
                average.rename(index=str, columns={'Response_Time': 'Response_Time_Mean'}, inplace=True)
                response_times.append(pd.DataFrame.merge(run_results, average, on=['Worker_Threads']))
            throughput_df = pd.concat(throughputs)
            response_time_df = pd.concat(response_times)
            summary = pd.merge(throughput_df, response_time_df, on=['Repetition', 'Worker_Threads', 'Type'])
            summary['Middlewares'] = mw_count
            summary['Memcached'] = mc_count
            summary_table = summary.reset_index().pivot_table(
                values=['Request_Throughput', 'Request_Throughput_Residual', 'Response_Time', 'Response_Time_Residual'],
                index=['Type', 'Memcached', 'Middlewares', 'Worker_Threads',
                       'Request_Throughput_Mean', 'Response_Time_Mean'], columns='Repetition')
            summary_table.columns = ['{}_{}'.format(x, y) for x, y in zip(summary_table.columns.get_level_values(0),
                                                                          summary_table.columns.get_level_values(1))]
            exp_tables_readable.append(summary_table)
            summary = summary[['Type', 'Memcached', 'Middlewares', 'Worker_Threads',
                               'Request_Throughput', 'Request_Throughput_Residual', 'Request_Throughput_Mean',
                               'Response_Time', 'Response_Time_Residual', 'Response_Time_Mean']]
            exp_tables.append(summary)

        result = pd.concat(exp_tables)

        if plot:
            result_throughput = result[['Type', 'Request_Throughput_Mean', 'Request_Throughput_Residual']]
            result_response_time = result[['Type', 'Response_Time_Mean', 'Response_Time_Residual']]

            throughput_get = result_throughput[~result_throughput.Type.str.contains('SET')]
            throughput_set = result_throughput[~result_throughput.Type.str.contains('GET')]
            response_time_get = result_response_time[~result_response_time.Type.str.contains('SET')]
            response_time_set = result_response_time[~result_response_time.Type.str.contains('GET')]

            PlottingFunctions.resplot(throughput_set, "Experiment 6 - SET", '6-0_throughput-set-residual',
                                      x='Request_Throughput_Mean',
                                      y='Request_Throughput_Residual', xlabel='Mean Request Throughput',
                                      ylabel='Residual')
            PlottingFunctions.resplot(throughput_get, "Experiment 6 - GET", '6-0_throughput-get-residual',
                                      x='Request_Throughput_Mean',
                                      y='Request_Throughput_Residual', xlabel='Mean Request Throughput',
                                      ylabel='Residual')
            PlottingFunctions.resplot(response_time_set, "Experiment 6 - SET", '6-0_response-time-set-residual',
                                      x='Response_Time_Mean',
                                      y='Response_Time_Residual', xlabel='Mean Response Time',
                                      ylabel='Residual')
            PlottingFunctions.resplot(response_time_get, "Experiment 6 - GET", '6-0_response-time-get-residual',
                                      x='Response_Time_Mean',
                                      y='Response_Time_Residual', xlabel='Mean Response Time',
                                      ylabel='Residual')

            PlottingFunctions.qqplot(throughput_set, "Experiment 6 - SET Throughput", '6-0_throughput-set-qq',
                                     x='Request_Throughput_Residual')
            PlottingFunctions.qqplot(throughput_get, "Experiment 6 - GET Throughput", '6-0_throughput-get-qq',
                                     x='Request_Throughput_Residual')
            PlottingFunctions.qqplot(response_time_set, "Experiment 6 - SET Response Time", '6-0_response-time-set-qq',
                                     x='Response_Time_Residual')
            PlottingFunctions.qqplot(response_time_get, "Experiment 6 - GET Response Time", '6-0_response-time-get-qq',
                                     x='Response_Time_Residual')

        result_print = pd.concat(exp_tables_readable)
        result_print = result_print.sort_values(by=['Type', 'Memcached', 'Middlewares', 'Worker_Threads'])

        print("2K Analysis Table:")
        print(result_print)
        print("====================\n")

    @staticmethod
    def experiment_0():
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_00())
        ExperimentPlotter.memtier_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_00(), 'Memtier')
        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_00())
        ExperimentPlotter.memtier_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_00(),
                                                            'Middleware')
        ExperimentPlotter.middleware_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_00())

    @staticmethod
    def experiment_2(to_plot):
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_21())
        ExperimentPlotter.memtier_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_21(), plot=to_plot)

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_22())
        ExperimentPlotter.memtier_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_22(), plot=to_plot)

    @staticmethod
    def experiment_3(to_plot):
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_31())
        ExperimentPlotter.memtier_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_31(), plot=to_plot)

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_31())
        ExperimentPlotter.middleware_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_31(), plot=to_plot)

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_32())
        ExperimentPlotter.memtier_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_32(), plot=to_plot)

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_32())
        ExperimentPlotter.middleware_statistics_get_set(data[0], ExperimentDefinitions.subexpriment_32(), plot=to_plot)

    @staticmethod
    def experiment_4(to_plot):
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_40())
        ExperimentPlotter.memtier_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_40(),
                                                            plot=to_plot)

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_40())
        ExperimentPlotter.middleware_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_40(),
                                                               plot=to_plot)

    @staticmethod
    def experiment_5(to_plot):
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_51(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_51(), 'Memtier', plot=to_plot)
        ExperimentPlotter.percentiles_multiget(data[1], ExperimentDefinitions.subexpriment_51(), 'Memtier',
                                               plot=to_plot)
        ExperimentPlotter.memtier_statistics_multiget(data[0], ExperimentDefinitions.subexpriment_51(), plot=to_plot)

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_51(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_51(), 'Middleware',
                                          plot=to_plot)
        ExperimentPlotter.middleware_statistics_multiget(data[0], ExperimentDefinitions.subexpriment_51(),
                                                         plot=to_plot)

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_52(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_52(), 'Memtier', plot=to_plot)
        ExperimentPlotter.percentiles_multiget(data[1], ExperimentDefinitions.subexpriment_52(), 'Memtier',
                                               plot=to_plot)
        ExperimentPlotter.memtier_statistics_multiget(data[0], ExperimentDefinitions.subexpriment_52(), plot=to_plot)

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_52(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_52(), 'Middleware',
                                          plot=to_plot)
        ExperimentPlotter.middleware_statistics_multiget(data[0], ExperimentDefinitions.subexpriment_52(),
                                                         plot=to_plot)

    @staticmethod
    def experiment_6(to_plot):
        mappings = []
        exp_list = [ExperimentDefinitions.subexpriment_60_1_1(), ExperimentDefinitions.subexpriment_60_2_1(),
                    ExperimentDefinitions.subexpriment_60_1_3(), ExperimentDefinitions.subexpriment_60_2_3()]
        for exp in exp_list:
            mappings.append([exp, ExperimentPlotter.memtier_experiment(exp)[0]])
        ExperimentPlotter.exp_6_pretty_table(mappings, plot=to_plot)

    @staticmethod
    def experiment_7(to_plot):
        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_40())
        maximum_service_rate_df = ExperimentPlotter.middleware_statistics_mmx(data[0],
                                                                              ExperimentDefinitions.subexpriment_40())
        muh_results = maximum_service_rate_df[['Worker_Threads', 'Maximum_Service_Rate']]
        lambs = ExperimentPlotter.middleware_statistics_request_family(data[0], ExperimentDefinitions.subexpriment_40(),
                                                                       plot=False)
        analysis_table = pd.DataFrame.merge(lambs, muh_results, on=['Worker_Threads'])
        mm1_results = StatisticsFunctions.mm1(analysis_table, plot=to_plot)
        mmm_results = StatisticsFunctions.mmm(analysis_table, plot=to_plot)
        mm1_pretty = mm1_results
        mmm_pretty = mmm_results
        mm1_pretty = mm1_pretty.pivot_table(index=['Num_Clients', 'Worker_Threads', 'Maximum_Service_Rate'],
                                            values=['Arrival_Rate', 'Traffic_Intensity', 'Mean_Number_Jobs_System',
                                                    'Measured_Response_Time', 'Estimated_Response_Time',
                                                    'Measured_Queue_Waiting_Time', 'Estimated_Queue_Waiting_Time',
                                                    'Measured_Queue_Size', 'Estimated_Queue_Size'])
        mmm_pretty = mmm_pretty.pivot_table(index=['Num_Clients', 'Worker_Threads', 'Maximum_Service_Rate'],
                                            values=['Arrival_Rate', 'Traffic_Intensity', 'Mean_Number_Jobs_System',
                                                    'Measured_Response_Time', 'Estimated_Response_Time',
                                                    'Measured_Queue_Waiting_Time', 'Estimated_Queue_Waiting_Time',
                                                    'Measured_Queue_Size', 'Estimated_Queue_Size',
                                                    'Probability_Zero_Jobs_System', 'Probability_Queueing',
                                                    'Mean_Average_Utilization_Each_Server'])
        print(mm1_pretty.round(decimals=2))
        print(mmm_pretty.round(decimals=2))


if __name__ == '__main__':
    sns.set(style="ticks", color_codes=True)#, context="paper")
    pd.set_option("display.width", 1920)
    pd.set_option("display.max_rows", 100)
    pd.set_option("display.max_columns", 64)
    pd.set_option('display.max_colwidth', 1920)
    # plt.rcParams["figure.figsize"] = [16, 9]

    figures_wanted = False

    #ExperimentPlotter.experiment_2(figures_wanted)
    ExperimentPlotter.experiment_3(figures_wanted)
    #ExperimentPlotter.experiment_4(figures_wanted)
    #ExperimentPlotter.experiment_5(figures_wanted)
    #ExperimentPlotter.experiment_6(figures_wanted)
    ExperimentPlotter.experiment_7(figures_wanted)
