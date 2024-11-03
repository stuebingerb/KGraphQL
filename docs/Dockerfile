FROM squidfunk/mkdocs-material:9.5.43@sha256:2c2802b4d26154eb2c30238ba8ed3aab3a6276009334fd99613e4c01e97cd420

COPY . /docs

RUN pip3 install --user -r requirements.txt
RUN mkdocs build


FROM nginx@sha256:28402db69fec7c17e179ea87882667f1e054391138f77ffaf0c3eb388efc3ffb
COPY --from=0 /docs/site /usr/share/nginx/html
