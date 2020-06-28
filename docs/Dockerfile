FROM squidfunk/mkdocs-material:5.3.2

COPY . /docs

RUN pip3 install --user -r requirements.txt
RUN mkdocs build


FROM nginx
COPY --from=0 /docs/site /usr/share/nginx/html
