#!/usr/bin/env bash
post(){ curl -F f=@"$1" 'https://drive.kkaii.xyz/projects/steamed/?replace=steamed-0.1.0.jar&f=steamed-0.1.0.jar&j&delete=steamed*';}
post build/libs/steamed-*.jar | jq .fileurl