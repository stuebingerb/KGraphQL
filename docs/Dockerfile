FROM squidfunk/mkdocs-material:5.3.2@sha256:a2193c0f9450252a9d63225180326f1f6ef73b1a7072be6247b4fd1a97bb96e3

COPY . /docs

RUN pip3 install --user -r requirements.txt
RUN mkdocs build


FROM nginx@sha256:28402db69fec7c17e179ea87882667f1e054391138f77ffaf0c3eb388efc3ffb
COPY --from=0 /docs/site /usr/share/nginx/html
