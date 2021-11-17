docker run -t --name heliumevents\
 --net elasticsearch --rm\
  -e ES_SERVER_ADDRESS=http://elasticsearch:9200\
   -e HOTSPOT=112Xa4p36ExdVDktAPFKx9zd8EwiMw7vC35hxyqiiYANC527BLiF\
    rdomloge/heliumevents