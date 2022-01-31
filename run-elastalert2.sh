docker run -ti --mount \
type=bind,source=/Users/rdomloge/heliumevents/elastalert2/my-config.yaml,target=/opt/elastalert/config.yaml \
-v /Users/rdomloge/heliumevents/elastalert2/my-config:/opt/elastalert/examples/rules \
jertel/elastalert2