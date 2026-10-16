#!/usr/bin/env python3
import json
import sys

with open(sys.argv[1]) as f:
    config = json.load(f)
client = config['client'][0]
values = {
    '@APP_ID@': client['client_info']['mobilesdk_app_id'],
    '@API_KEY@': client['api_key'][0]['current_key'],
    '@PROJECT_ID@': config['project_info']['project_id'],
    '@SENDER_ID@': config['project_info']['project_number'],
}
scenario = sys.stdin.read()
for placeholder, value in values.items():
    scenario = scenario.replace(placeholder, value)
sys.stdout.write(scenario)
