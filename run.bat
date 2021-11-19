set VER=1.0.3
set JAMES=112KVEvBawFScQeXgmzLGAZLG3mAozQFchig3NzPKBi5FYKvQGdk
set ME=112Xa4p36ExdVDktAPFKx9zd8EwiMw7vC35hxyqiiYANC527BLiF

FOR /L %%A IN (1,1,200) DO (
  rem docker run -ti --name heliumevents --rm --net elasticsearch -e ES_SERVER_ADDRESS=http://elasticsearch:9200 -e USE_STAKEJOY_API=true -e HOTSPOT=%ME% rdomloge/heliumevents:%VER%  
  docker run -ti --name heliumevents --rm --net elasticsearch -e ES_SERVER_ADDRESS=http://elasticsearch:9200 -e USE_STAKEJOY_API=true -e HOTSPOT=%JAMES% rdomloge/heliumevents:%VER%
)

