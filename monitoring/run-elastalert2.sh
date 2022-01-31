CWD=$(pwd) && docker run -ti --mount \
type=bind,source=$CWD/my-config.yaml,target=/opt/elastalert/config.yaml \
--mount type=bind,source=$CWD/elastalert2-alerts,target=/opt/elastalert/rules \
jertel/elastalert2