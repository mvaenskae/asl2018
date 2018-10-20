#!/usr/bin/env bash

vagrant box add --provider virtualbox bento/ubuntu-16.04
vagrant mutate bento/ubuntu-16.04 libvirt
vagrant up
