import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import numpy as np

from experiment_definitions import ExperimentDefinitions
from data_collectors import MemtierCollector, MiddlewareCollector


class PlottingFunctions:

    @staticmethod
    def lineplot(dataframe, experiment_title, save_as_filename,
                 x=None, y=None, hue=None, style=None, ci='sd', err_style='bars',
                 xlabel=None, ylabel=None, huelabel=None, stylelabel=None,
                 xlim=(0, None), ylim=(0, None),
                 xticks=None):
        sns.lineplot(x, y, data=dataframe, legend="full", hue=hue, style=style,
                     ci=ci, err_style='band').set(xlabel=xlabel, ylabel=ylabel,
                                                     title=experiment_title,
                                                     xlim=xlim, ylim=ylim, )
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
    def plot_throughput_single(dataframe, experiment_title, save_as_filename,
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
                             x='Num_Clients', y='Response_Time', hue='RequestType', style='Worker_Threads', ci='sd',
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
    def plot_response_time_single(dataframe, experiment_title, save_as_filename,
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
        return dataframe[aggregate_on].agg(['mean', 'std']).rename(index=str, columns={"mean": aggregate_on + '_Mean',
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


class ExperimentPlotter:

    @staticmethod
    def save_figure(save_as_filename):
        plt.savefig(save_as_filename)

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
    def throughput_response_time_get_set(flattened, subexperiment, measured_from, plot=True, get_individual_runs=False):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)
        set_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])
        get_group = flattened[1].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        if not get_individual_runs:
            throughput_set = StatisticsFunctions.get_sum(set_group, 'Request_Throughput')
            throughput_get = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
            response_time_set = StatisticsFunctions.get_average(set_group, 'Response_Time')
            response_time_get = StatisticsFunctions.get_average(get_group, 'Response_Time')

            if plot:
                concatenated_throughput = pd.concat([throughput_set.assign(RequestType='SET'),
                                                     throughput_get.assign(RequestType='GET')])
                concatenated_response_time = pd.concat([response_time_set.assign(RequestType='SET'),
                                                  response_time_get.assign(RequestType='GET')])

                throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
                throughput_interactive = concatenated_throughput[concatenated_throughput.Type.str.contains('Interactive')]

                response_time_measured = concatenated_response_time[~concatenated_response_time.Type.str.contains('Interactive')]
                response_time_interactive = concatenated_response_time[concatenated_response_time.Type.str.contains('Interactive')]

                PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, None)
                PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name, None)

                PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law', None)
                PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law', None)

            plotted_throughput_set = throughput_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
            plotted_throughput_get = throughput_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
            plotted_response_time_set = response_time_set.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
            plotted_response_time_get = response_time_get.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

            throughout_set_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_set,
                                                                             'Request_Throughput')
            throughput_get_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_get,
                                                                             'Request_Throughput')
            response_time_set_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_set, 'Response_Time')
            response_time_get_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_get, 'Response_Time')

            print(exp_name + " SET:")
            print(throughout_set_plotted)
            print("====================\n")

            print(exp_name + " GET:")
            print(throughput_get_plotted)
            print("====================\n")

            print(exp_name + " SET:")
            print(response_time_set_plotted)
            print("====================\n")

            print(exp_name + " GET:")
            print(response_time_get_plotted)
            print("====================\n")
        else:
            print(exp_name + " SET:")
            print(StatisticsFunctions.get_sum(set_group, 'Request_Throughput'))
            print("====================\n")

            print(exp_name + " GET:")
            print(StatisticsFunctions.get_sum(get_group, 'Request_Throughput'))
            print("====================\n")

            print(exp_name + " SET:")
            print(StatisticsFunctions.get_average(set_group, 'Response_Time'))
            print("====================\n")

            print(exp_name + " GET:")
            print(StatisticsFunctions.get_average(get_group, 'Response_Time'))
            print("====================\n")

    @staticmethod
    def throughput_response_time_single_request_type(flattened, subexperiment, measured_from, r_type='SET', plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)
        single_group = flattened[0].groupby(['Num_Clients', 'Repetition', 'Worker_Threads', 'Type'])

        throughput_single = StatisticsFunctions.get_sum(single_group, 'Request_Throughput')
        response_time_single = StatisticsFunctions.get_average(single_group, 'Response_Time')

        if plot:
            concatenated_throughput = pd.concat([throughput_single.assign(RequestType=r_type)])
            concatenated_response_time = pd.concat([response_time_single.assign(RequestType=r_type)])

            throughput_measured = concatenated_throughput[~concatenated_throughput.Type.str.contains('Interactive')]
            throughput_interactive = concatenated_throughput[concatenated_throughput.Type.str.contains('Interactive')]

            response_time_measured = concatenated_response_time[
                ~concatenated_response_time.Type.str.contains('Interactive')]
            response_time_interactive = concatenated_response_time[
                concatenated_response_time.Type.str.contains('Interactive')]

            PlottingFunctions.plot_throughput_by_type(throughput_measured, exp_name, None)
            PlottingFunctions.plot_response_time_by_type(response_time_measured, exp_name, None)

            PlottingFunctions.plot_throughput_by_type(throughput_interactive, exp_name + ' Interactive Law', None)
            PlottingFunctions.plot_response_time_by_type(response_time_interactive, exp_name + ' Interactive Law', None)

        plotted_throughput_set = throughput_single.groupby(['Num_Clients', 'Worker_Threads', 'Type'])
        plotted_response_time_set = response_time_single.groupby(['Num_Clients', 'Worker_Threads', 'Type'])

        throughput_single_plotted = StatisticsFunctions.get_average_and_std(plotted_throughput_set,
                                                                            'Request_Throughput')
        response_time_single_plotted = StatisticsFunctions.get_average_and_std(plotted_response_time_set, 'Response_Time')

        print(exp_name + " " + r_type + ":")
        print(throughput_single_plotted)
        print("====================\n")

        print(exp_name + " " + r_type + ":")
        print(response_time_single_plotted)
        print("====================\n")

    @staticmethod
    def histogram_6keys(flattened, subexperiment, measured_from, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)

        if subexperiment['subexperiment_id'] == 2:
            req_type = 'MULTIGET_6'
        else:
            req_type = 'SHARDED_6'

        no_interactive_law = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        summed_get_bucket = (no_interactive_law[no_interactive_law['Type'] == req_type]).groupby(['Bucket', 'Repetition']).agg(
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
            PlottingFunctions.plot_histogram(histogram, exp_name, None)

    @staticmethod
    def percentiles_multiget(flattened, subexperiment, measured_from, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)
        no_interactive_law = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        summed_get_bucket = no_interactive_law.groupby(['Bucket', 'Repetition', 'Type']).agg({'Count': np.sum}).reset_index()

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
            quantile = StatisticsFunctions.get_percentiles(item.groupby(['Request_Type', 'Repetition'])['Response_Time'])
            quantile_data_per_type_and_repetition.append(quantile)

        # The actual final dataframe which holds all quantile values
        percentiles = pd.concat(quantile_data_per_type_and_repetition)

        if plot:
            PlottingFunctions.lineplot(percentiles, exp_name, None, x='Percentile', y='Response_Time', hue='Request_Type',
                                       xlabel='Percentile', ylabel='Response Time (ms)', huelabel='MultiGET Type',
                                       xlim=(0, 1), ylim=(0, None), xticks=[0.25, 0.5, 0.75, 0.9, 0.99])

        print(exp_name + " GET Percentiles:")
        print(StatisticsFunctions.get_average_and_std(percentiles.groupby(['Request_Type', 'Percentile']), 'Response_Time'))
        print("====================\n\n")

    @staticmethod
    def throughput_response_time_multiget(flattened, subexperiment, measured_from, plot=True):
        exp_name = "Experiment {}.{} - {}".format(subexperiment['experiment_id'], subexperiment['subexperiment_id'],
                                                  measured_from)

        if subexperiment['subexperiment_id'] == 2:
            req_types = 'Non-sharded MultiGET'
        else:
            req_types = 'Sharded MultiGET'

        get_group = flattened[1][~flattened[1].Type.str.contains('Interactive')]
        get_group = get_group.groupby(['Type', 'Repetition', 'Worker_Threads'])


        summed_get_throughput = StatisticsFunctions.get_sum(get_group, 'Request_Throughput')
        average_get_response_time = StatisticsFunctions.get_average(get_group, 'Response_Time')

        concatenated_throughput = pd.concat([summed_get_throughput.assign(RequestType='GET')])
        concatenated_response_time = pd.concat([average_get_response_time.assign(RequestType='GET')])

        if plot:
            PlottingFunctions.lineplot(concatenated_throughput, exp_name, None, x='Type', y='Request_Throughput',
                                       xlabel=req_types, ylabel='Throughput (req/s)',
                                       xlim=(None, None), ylim=(0, 4000), xticks=(np.arange(4), [1, 3, 6, 9]))

            PlottingFunctions.lineplot(concatenated_response_time, exp_name, None, x='Type', y='Response_Time',
                                       xlabel=req_types, ylabel='Response Time (ms)',
                                       xlim=(None, None), ylim=(0, None), xticks=(np.arange(4), [1, 3, 6, 9]))

        plotted_throughput_get = summed_get_throughput.groupby(['Type'])
        plotted_response_time_get = average_get_response_time.groupby(['Type'])

        throughput_flattened_get = StatisticsFunctions.get_average_and_std(plotted_throughput_get, 'Request_Throughput')
        response_time_flattened_get = StatisticsFunctions.get_average_and_std(plotted_response_time_get, 'Response_Time')

        print(exp_name + " GET:")
        print(throughput_flattened_get)
        print("====================\n\n")

        print(exp_name + " GET:")
        print(response_time_flattened_get)
        print("====================\n\n")

    @staticmethod
    def experiment_2():
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_21())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_21(), 'Memtier')

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_22())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_22(), 'Memtier')

    @staticmethod
    def experiment_3():
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_31())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_31(), 'Memtier')

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_31())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_31(), 'Middleware')
        # TODO: Plot other middleware stats

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_32())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_32(), 'Memtier')

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_32())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_32(), 'Middleware')
        # TODO: Plot other middleware stats

    @staticmethod
    def experiment_4():
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_40())
        ExperimentPlotter.throughput_response_time_single_request_type(data[0], ExperimentDefinitions.subexpriment_40(), 'Memtier')
        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_40())
        ExperimentPlotter.throughput_response_time_single_request_type(data[0], ExperimentDefinitions.subexpriment_40(), 'Middleware')
        # TODO: Plot other middleware stats

    @staticmethod
    def experiment_5():
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_51(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_51(), 'Memtier')
        ExperimentPlotter.percentiles_multiget(data[1], ExperimentDefinitions.subexpriment_51(), 'Memtier')
        ExperimentPlotter.throughput_response_time_multiget(data[0], ExperimentDefinitions.subexpriment_51(), 'Memtier')

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_51(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_51(), 'Middleware')
        ExperimentPlotter.throughput_response_time_multiget(data[0], ExperimentDefinitions.subexpriment_51(), 'Middleware')
        # TODO: Plot other middleware stats

        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_52(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_52(), 'Memtier')
        ExperimentPlotter.percentiles_multiget(data[1], ExperimentDefinitions.subexpriment_52(), 'Memtier')
        ExperimentPlotter.throughput_response_time_multiget(data[0], ExperimentDefinitions.subexpriment_52(), 'Memtier')

        data = ExperimentPlotter.middleware_experiment(ExperimentDefinitions.subexpriment_52(), histogram=True)
        ExperimentPlotter.histogram_6keys(data[1], ExperimentDefinitions.subexpriment_52(), 'Middleware')
        ExperimentPlotter.throughput_response_time_multiget(data[0], ExperimentDefinitions.subexpriment_52(), 'Middleware')
        # TODO: Plot other middleware stats

    @staticmethod
    def experiment_6():
        print("1 Middleware, 1 Server:")
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_60_1_1())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_60_1_1(), 'Memtier',
                                                           plot=False, get_individual_runs=True)
        print("\n\n\n1 Middleware, 3 Servers:")
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_60_1_3())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_60_1_3(), 'Memtier',
                                                           plot=False, get_individual_runs=True)
        print("\n\n\n2 Middlewares, 1 Server:")
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_60_2_1())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_60_2_1(), 'Memtier',
                                                           plot=False, get_individual_runs=True)
        print("\n\n\n2 Middlewares, 3 Servers:")
        data = ExperimentPlotter.memtier_experiment(ExperimentDefinitions.subexpriment_60_2_3())
        ExperimentPlotter.throughput_response_time_get_set(data[0], ExperimentDefinitions.subexpriment_60_2_3(), 'Memtier',
                                                           plot=False, get_individual_runs=True)


if __name__ == '__main__':
    sns.set(style="ticks", color_codes=True, context="paper")
    pd.set_option("display.width", 250)
    pd.set_option("display.max_rows", 100)
    pd.set_option("display.max_columns", 64)

    ExperimentPlotter.experiment_2()
    ExperimentPlotter.experiment_3()
    ExperimentPlotter.experiment_4()
    ExperimentPlotter.experiment_5()
    ExperimentPlotter.experiment_6()
