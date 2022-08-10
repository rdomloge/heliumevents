curl -H 'Content-Type: application/json' -X POST --data-binary '{ "position": 0 }' 'http://10.0.0.10:9200/clean-rose-parakeet-miner/_doc/info'
curl -H 'Content-Type: application/json' -X POST --data-binary '{"properties":{"timestamp":{"type": "date", "format": "yyyy-MM-dd HH:mm:ss Z z"}}}' 'http://10.0.0.10:9200/clean-rose-parakeet-miner/_mapping/' 
