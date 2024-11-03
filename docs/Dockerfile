FROM squidfunk/mkdocs-material:5.5.14@sha256:49dfcd6bc822a10a5072ac96d20ad7065c7ac414ac43aeb1358c718b5bc2895b

COPY . /docs

RUN pip3 install --user -r requirements.txt
RUN mkdocs build


FROM nginx@sha256:28402db69fec7c17e179ea87882667f1e054391138f77ffaf0c3eb388efc3ffb
COPY --from=0 /docs/site /usr/share/nginx/html
