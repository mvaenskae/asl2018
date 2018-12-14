from experiment_definitions import ExperimentDefinitions


class PathHelper:
    home = ExperimentDefinitions.log_base
    repetitions = ExperimentDefinitions.repetitions

    def __init__(self, experiment_dictionary):
        self.experiment_id = experiment_dictionary.get('experiment_id')
        self.subexpertiment_id = experiment_dictionary.get('subexperiment_id')
        self.worker_threads = experiment_dictionary.get('worker_threads')
        self.memtier_threads = experiment_dictionary.get('memtier_threads')
        self.memtier_clients = experiment_dictionary.get('memtier_clients')
        self.hostnames = experiment_dictionary.get('hostnames')
        self.request_types = experiment_dictionary.get('request_types')
        self.memcached_servers = experiment_dictionary.get('memcached_servers')
        self.memtier_targets = experiment_dictionary.get('memtier_targets')
        self.generated_paths = []

    def generate_paths(self):
        """
        Method to generate depth-first search all paths where files are expected to exist for an experiment ans store it
        in the instance-local generated_paths field.
        :return: Nothing
        """
        for wt in self.worker_threads:
            for ct in self.memtier_threads:
                for vc in self.memtier_clients:
                    for rep in PathHelper.repetitions:
                        for host in self.hostnames:
                            for type in self.request_types:
                                path = PathHelper.home.joinpath(str(self.experiment_id), str(self.subexpertiment_id),
                                                                f'{wt:02}', str(ct), f'{vc:02}', str(rep), str(host),
                                                                str(type), str(len(self.memcached_servers)))
                                self.generated_paths.append(path)

    def generate_paths_by_repetition(self):
        result = []
        for wt in self.worker_threads:
            for ct in self.memtier_threads:
                for vc in self.memtier_clients:
                    for host in self.hostnames:
                        for type in self.request_types:
                            for rep in PathHelper.repetitions:
                                path = PathHelper.home.joinpath(str(self.experiment_id), str(self.subexpertiment_id),
                                                                f'{wt:02}', str(ct), f'{vc:02}', str(rep), str(host),
                                                                str(type), str(len(self.memcached_servers)))
                                result.append(path)
        return result

    def generate_paths_by_repetition_distinct_hosts(self):
        result = []
        for wt in self.worker_threads:
            for host in self.hostnames:
                for type in self.request_types:
                    for ct in self.memtier_threads:
                        for vc in self.memtier_clients:
                            for rep in PathHelper.repetitions:
                                path = PathHelper.home.joinpath(str(self.experiment_id), str(self.subexpertiment_id),
                                                                f'{wt:02}', str(ct), f'{vc:02}', str(rep), str(host),
                                                                str(type), str(len(self.memcached_servers)))
                                result.append(path)
        return result

    def generate_paths_by_repetition_distinct_hosts_after_types(self):
        result = []
        for wt in self.worker_threads:
            for type in self.request_types:
                for host in self.hostnames:
                    for ct in self.memtier_threads:
                        for vc in self.memtier_clients:
                            for rep in PathHelper.repetitions:
                                path = PathHelper.home.joinpath(str(self.experiment_id), str(self.subexpertiment_id),
                                                                f'{wt:02}', str(ct), f'{vc:02}', str(rep), str(host),
                                                                str(type), str(len(self.memcached_servers)))
                                result.append(path)
        return result

    @staticmethod
    def filter_paths_for_creator(path_list, machine_name):
        """
        Return only paths which belong to creator of the data.
        :param path_list: List of paths to filter.
        :param machine_name: Creator of log files to filter for.
        :return: List of paths belonging to creator.
        """
        return [path for path in path_list if path.parts[-3] == machine_name]

    @staticmethod
    def interpret_path(path):
        """
        Returns the path's interpretation for scripts to use.
        :param path: Path to interpret
        :return: Dictionary of path interpretation
        """
        exp_id = path.parts[-9]
        subexp_id = path.parts[-8]
        wt_count = path.parts[-7]
        ct_count = path.parts[-6]
        vc_count = path.parts[-5]
        rep_count = path.parts[-4]
        hostname = path.parts[-3]
        type = path.parts[-2]
        memcached_server_count = path.parts[-1]

        return {'exp_id': exp_id, 'subexp_id': subexp_id,
                'wt': wt_count, 'ct': ct_count, 'vc': vc_count, 'rep': rep_count,
                'hostname': hostname, 'type': type, 'memcached_server_count': memcached_server_count}


    @staticmethod
    def chdir_host(path, new_host):
        exp_id = path.parts[-9]
        subexp_id = path.parts[-8]
        wt_count = path.parts[-7]
        ct_count = path.parts[-6]
        vc_count = path.parts[-5]
        rep_count = path.parts[-4]
        type = path.parts[-2]
        memcached_server_count = path.parts[-1]

        return PathHelper.home.joinpath(exp_id, subexp_id, wt_count, ct_count, vc_count, rep_count, new_host, type,
                                        memcached_server_count)
