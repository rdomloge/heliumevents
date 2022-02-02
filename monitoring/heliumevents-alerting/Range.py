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
title = configdoc.get('title', "Range issue")

print("Using field {}, min {}, max {} and webhook {}".format(field, min, max, webhook))

for line in sys.stdin:
    inputdoc += line
    pass

inputdoc = json.loads(inputdoc)

fieldValue = inputdoc[field]
print("{} value {}".format(field, fieldValue))
body = {}
body['username'] = title

if(fieldValue > max or fieldValue < min):
    print("{} {} is too {}".format(field, fieldValue, "low" if fieldValue < min else "high"))
    body['content'] = "{} {} is not between {} and {}".format(field, fieldValue, min, max)
    response = requests.post(webhook, 
        data = json.dumps(body),
        headers={"content-type": "application/json"})
    print("Webhook called response '{}', message: '{}'".format(response.status_code, response.content))
else:
    print("{} {} is acceptable".format(field, fieldValue))
