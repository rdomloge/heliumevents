FROM ubuntu

RUN apt update 
RUN apt-get install -y git
RUN git clone https://github.com/Yelp/elastalert.git
WORKDIR elastalert
RUN apt-get install -y python3 python3-pip
RUN pip install "setuptools>=11.3"
RUN python3 setup.py install