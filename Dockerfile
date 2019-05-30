FROM alpine:3.8 as gobuider

WORKDIR /root/go

RUN apk add --no-cache go git build-base

COPY src/go .

RUN go get -d -v ./...

RUN CGO_ENABLED=0 go install -v -ldflags '-extldflags "-static"' github.com/edgexfoundry/go-ui-server

FROM clojure:lein-2.7.1-alpine as clojurebuilder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN apk add --no-cache nodejs-npm
RUN npm install && npm install react
RUN lein with-profile cljs run -m shadow.cljs.devtools.cli release main

FROM scratch
WORKDIR /root/
COPY --from=gobuider /root/go/bin/go-ui-server .
COPY --from=clojurebuilder /usr/src/app/resources/public assets
COPY --from=clojurebuilder /usr/src/app/resources/configuration.toml res/configuration.toml
ENV PORT=8080
ENV DATA_FILE=/edgex-manager/data/password
EXPOSE $PORT

# Declare volumes to mount
VOLUME ["/edgex-manager/data"]

ENTRYPOINT ["/root/go-ui-server"]
