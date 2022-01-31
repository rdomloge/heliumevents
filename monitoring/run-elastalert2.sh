CWD=$(pwd) && docker run -ti --mount type=bind,source=$CWD/my-config.yaml,\
target=/opelastalert/config.yaml -v \
$CWD/elastalert2-alerts:/opt/elastalert/examples/rules \
jertel/elastalert2