#!/usr/bin/env python

import fileinput
import yaml
import sys
import json
import requests

inputdoc = ""
configdoc = ""

field =""
webhook =""


if len(sys.argv) == 1:
    sys.exit("Please provide a single argument which is the path to the config file. Your args: {}".format(sys.argv))

print("Using config file {}".format(sys.argv[1]))


with open(sys.argv[1], "r") as stream:
    try:
        configdoc = yaml.safe_load(stream)
    except yaml.YAMLError as exc:
        print(exc)

fieldPath = configdoc['field']
webhook = configdoc['webhook']
stateIdentifier = configdoc['stateIdentifier']
title = configdoc.get('title', "Change detected")
es_addr = configdoc['es_addr']

print("Monitoring field {}, for change and using webhook".format(fieldPath))

def load_previous_state(stateIdentifier):
    response = requests.get('{}/heliumevents-alerts-state/_doc/change-{}/_source'.format(es_addr, stateIdentifier))
    if(response.status_code == 200): 
        data = json.loads(response.content)
        return data['value']
    elif(response.status_code == 404):
        print("No previous value")
        pass
    else: sys.exit("Could not load previous state. Response code {} with message {}".format(response.status_code, response.content))

def save_new_state(stateIdentifier, newValue):
    body = {}
    body['value'] = newValue
    response = requests.post('{}/heliumevents-alerts-state/_doc/change-{}'.format(es_addr, stateIdentifier), data = json.dumps(body),
        headers={"content-type": "application/json"})
    if(response.status_code != 201): print("Could not save new value! Response from ES was {} with message '{}'".format(response.status_code, response.content))
    else: print("New value {} for field {} stored".format(fieldPath, newValue))

for line in sys.stdin:
    inputdoc += line
    pass

inputdoc = json.loads(inputdoc)

def extractValue(fields):
    doc = inputdoc
    for field in fields:
        doc = doc[field]
    return doc
fieldValue = extractValue(fieldPath)

body = {}
body['username'] = title

currentValue = load_previous_state(stateIdentifier)

if(fieldValue != currentValue):
    print("{} has changed from {} to {}".format(fieldPath, currentValue, fieldValue))
    save_new_state(stateIdentifier, fieldValue)
    body['content'] = "{} has changed from {} to {}".format(fieldPath, currentValue, fieldValue)
    response = requests.post(webhook, 
        data = json.dumps(body),
        headers={"content-type": "application/json"})
    print("Webhook called response '{}', message: '{}'".format(response.status_code, response.content))
else:
    print("{} is unchanged at {}".format(fieldPath, fieldValue))


