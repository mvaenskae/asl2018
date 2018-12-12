import pathlib


class ExperimentDefinitions:
    """
    Helper class which holds constant definitions for the experimental parameters for each experiment
    """

    log_base = pathlib.Path.home().joinpath("ASL_RESULTS")
    repetitions = [1, 2, 3]

    @staticmethod
    def subexpriment_00():
        return {'experiment_id': 0,
                'subexperiment_id': 0,
                'worker_threads': [8, 16, 32, 64],
                'memtier_threads': [1],
                'memtier_clients': [16, 32, 40, 48, 56],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['SET'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_21():
        return {'experiment_id': 2,
                'subexperiment_id': 1,
                'worker_threads': [0],
                'memtier_threads': [2],
                'memtier_clients': [1, 2, 4, 8, 16, 32, 44, 56, 64],
                'hostnames': ['Client1', 'Client2', 'Client3'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1'],
                'memtier_targets': ['Server1']}

    @staticmethod
    def subexpriment_22():
        return {'experiment_id': 2,
                'subexperiment_id': 2,
                'worker_threads': [0],
                'memtier_threads': [1],
                'memtier_clients': [1, 2, 4, 8, 16, 32, 44, 56, 64],
                'hostnames': ['Client1'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1', 'Server2'],
                'memtier_targets': ['Server1', 'Server2']}

    @staticmethod
    def subexpriment_31():
        return {'experiment_id': 3,
                'subexperiment_id': 1,
                'worker_threads': [8, 16, 32, 64],
                'memtier_threads': [2],
                'memtier_clients': [1, 2, 4, 8, 16, 32, 48],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1'],
                'memtier_targets': ['Middleware1']}

    @staticmethod
    def subexpriment_32():
        return {'experiment_id': 3,
                'subexperiment_id': 2,
                'worker_threads': [8, 16, 32, 64],
                'memtier_threads': [1],
                'memtier_clients': [1, 2, 4, 8, 16, 32, 48],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_40():
        return {'experiment_id': 4,
                'subexperiment_id': 0,
                'worker_threads': [8, 16, 32, 64],
                'memtier_threads': [1],
                'memtier_clients': [1, 2, 4, 8, 16, 32, 48],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['SET'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_51():
        return {'experiment_id': 5,
                'subexperiment_id': 1,
                'worker_threads': [64],
                'memtier_threads': [1],
                'memtier_clients': [2],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['SHARDED_1', 'SHARDED_3', 'SHARDED_6', 'SHARDED_9'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_52():
        return {'experiment_id': 5,
                'subexperiment_id': 2,
                'worker_threads': [64],
                'memtier_threads': [1],
                'memtier_clients': [2],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['MULTIGET_1', 'MULTIGET_3', 'MULTIGET_6', 'MULTIGET_9'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_60_1_1():
        return {'experiment_id': 6,
                'subexperiment_id': 0,
                'worker_threads': [8, 32],
                'memtier_threads': [2],
                'memtier_clients': [32],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1'],
                'memtier_targets': ['Middleware1']}

    @staticmethod
    def subexpriment_60_1_3():
        return {'experiment_id': 6,
                'subexperiment_id': 0,
                'worker_threads': [8, 32],
                'memtier_threads': [2],
                'memtier_clients': [32],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1']}

    @staticmethod
    def subexpriment_60_2_1():
        return {'experiment_id': 6,
                'subexperiment_id': 0,
                'worker_threads': [8, 32],
                'memtier_threads': [1],
                'memtier_clients': [32],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def subexpriment_60_2_3():
        return {'experiment_id': 6,
                'subexperiment_id': 0,
                'worker_threads': [8, 32],
                'memtier_threads': [1],
                'memtier_clients': [32],
                'hostnames': ['Client1', 'Client2', 'Client3', 'Middleware1', 'Middleware2'],
                'request_types': ['GET', 'SET'],
                'memcached_servers': ['Server1', 'Server2', 'Server3'],
                'memtier_targets': ['Middleware1', 'Middleware2']}

    @staticmethod
    def all_experiments():
        experiment2 = {'1': ExperimentDefinitions.subexpriment_21(), '2': ExperimentDefinitions.subexpriment_22()}
        experiment3 = {'1': ExperimentDefinitions.subexpriment_31(), '2': ExperimentDefinitions.subexpriment_32()}
        experiment4 = {'0': ExperimentDefinitions.subexpriment_40()}
        experiment5 = {'1': ExperimentDefinitions.subexpriment_51(), '2': ExperimentDefinitions.subexpriment_52()}
        experiment6 = {'1-1': ExperimentDefinitions.subexpriment_60_1_1(),
                       '1-3': ExperimentDefinitions.subexpriment_60_1_3(),
                       '2-1': ExperimentDefinitions.subexpriment_60_2_1(),
                       '2-3': ExperimentDefinitions.subexpriment_60_2_3()}

        experiments = {'2': experiment2, '3': experiment3, '4': experiment4, '5': experiment5, '6': experiment6}
        return experiments

    @staticmethod
    def all_subexperiments():
        experiments = {'21': ExperimentDefinitions.subexpriment_21(), '22': ExperimentDefinitions.subexpriment_22(),
                       '31': ExperimentDefinitions.subexpriment_31(), '32': ExperimentDefinitions.subexpriment_32(),
                       '40': ExperimentDefinitions.subexpriment_40(),
                       '51': ExperimentDefinitions.subexpriment_51(), '52': ExperimentDefinitions.subexpriment_52(),
                       '60_1-1': ExperimentDefinitions.subexpriment_60_1_1(),
                       '60_1-3': ExperimentDefinitions.subexpriment_60_1_3(),
                       '60_2-1': ExperimentDefinitions.subexpriment_60_2_1(),
                       '60_2-3': ExperimentDefinitions.subexpriment_60_2_3()}

        return experiments
