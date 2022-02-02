#!/usr/bin/env python

import fileinput
import yaml
import sys
import json
import requests

inputdoc = ""
configdoc = ""

field =""
min =""
max =""
webhook =""


if len(sys.argv) == 1:
    sys.exit("Please provide a single argument which is the path to the config file. Your args: {}".format(sys.argv))

print("Using config file {}".format(sys.argv[1]))


with open(sys.argv[1], "r") as stream:
    try:
        configdoc = yaml.safe_load(stream)
    except yaml.YAMLError as exc:
        print(exc)

field = configdoc['field']
min = configdoc['min']
max = configdoc['max']
webhook = configdoc['webhook']
ignoreSslErrors = configdoc['ignoreSslErrors']
print("Using field {}, min {}, max {} and webhook {}".format(field, min, max, webhook))

for line in sys.stdin:
    inputdoc += line
    pass

inputdoc = json.loads(inputdoc)

fieldValue = inputdoc[field]
print("{} value {}".format(field, fieldValue))
if(fieldValue < min):
    print("{} {} is too low".format(field, fieldValue))
    requests.get(webhook, headers={"Content-Type": "application/json"}, data = {"username":"Range issue", "content": "{} is lower than {} :: {}".format(field, min, fieldValue), verify:ignoreSslErrors})
elif(fieldValue > max):
    print("{} {} is too high".format(field, fieldValue))
    response = requests.get(webhook, 
        data = {"username":"Range issue", "content": "{} is higher than {} :: {}".format(field, max, fieldValue)},
        headers={"content-type": "application/json"})
    print("Webhook called '{}' code, '{}' message".format(response.status_code, response.content))
else:
    print("{} {} is acceptable".format(field, fieldValue))
