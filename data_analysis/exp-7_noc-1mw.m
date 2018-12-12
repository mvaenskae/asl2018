pkg load queueing
clear

% Experiment 3.1 based computations with 3 clients, 1 middleware and 1 server
% The following design is used in arrays: [SET; GET]

m = 64;
vcs = [6 12 24 48 96 192];
experiment = 1; % Set to 1 for SET values of report, 2 for GET values of report.

S_client = 0;
S_request = 0.0003;
S_netthread = 1/11545;
S_worker = [ 0.172963 1.094205 ] / 1000;
S_server = [ 1/16335 1/2940 ];

P = [ 0 1 0 0 0 0;
      0 0 1 0 0 0;
      0 0 0 1 0 0;
      0 0 0 0 1 0;
      0 0 0 0 0 1;
      1 0 0 0 0 0 ];
V = qncsvisits(P);

Q_connections = {
    qnmknode("-/g/inf", S_client);
    qnmknode("-/g/inf", S_request);
    qnmknode("m/m/m-fcfs", S_netthread);
    qnmknode("m/m/m-fcfs", S_worker(experiment), m);
    qnmknode("-/g/inf", S_request);
    qnmknode("m/m/m-fcfs", S_server(experiment));
};

i = 0;
m_U = [];
m_R = [];
m_Q = [];
m_X = [];
X_mva = [];
for n=vcs
  i++
  [ U R Q X ] = qnsolve("closed", n, Q_connections, V);
  m_U(i,:) = [n U];
  m_R(i,:) = [n R];
  m_Q(i,:) = [n Q];
  m_X(i,:) = [n X];
  % X_mva(i,:) = [n X(3)/V(3)];
endfor
close all;

display('Matrix for U:')
disp(m_U(:,[1,3:end]))
display('Matrix for R:')
disp(m_R(:,[1,3:end]) * 1000)
display('Matrix for Q:')
disp(m_Q(:,[1,3:end]))
display('Matrix for X:')
disp(m_X(:,[1:2]))
