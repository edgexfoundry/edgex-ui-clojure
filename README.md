# EdgeX Manager UI

The main project source is in `src/main`. Built using the Fulcro Clojure framework
[http://fulcro.fulcrologic.com/](http://fulcro.fulcrologic.com/). The Fulcro web site has a Developer's Guide
and links to a series of Youtube training videos.

The Clojure build tool [Leiningen](https://leiningen.org/) and the [shadow-cljs](http://shadow-cljs.org/) ClojureScript system are
used.

A brief introduction to the GUI, including using a pre-built docker image, is available [here](docs/Manager.rst).

The UI was developed using CSS from the commercial version of [Creative Tim](https://www.creative-tim.com/)'s Light
Bootstrap Dashboard for Bootstrap 3. Note that the commercial version is now for Bootstrap 4.
The CSS from the free open source version of the
[dashboard](https://github.com/creativetimofficial/light-bootstrap-dashboard) has been substituted and a collapsible
menu feature disabled.

The backend server is implemented in Go.

A Dockerfile is proved to create a Dockerized server.

```
├── Dockerfile                                     Dockerfile to build a deployable server
├── docs
├── resources
│   ├── configuration.toml                         Go server docker deployment configuration
└── src
    ├── go
    │   ├── res
    │   │   └── configuration.toml                 Go server development configuration
    │   └── src
    │       └── github.com
    │           └── edgexfoundry
    │               └── go-ui-server
    │                   ├── internal
    │                   │   ├── edgex
    │                   │   │   ├── common.go      Common constants
    │                   │   │   ├── config.go      Runtime configuration support
    │                   │   │   ├── endpoints.go   REST server endpoint support
    │                   │   │   └── services.go    Query and mutation mapping to EdgeX REST services
    │                   │   └── fulcro
    │                   │       ├── content.go     Transit content type support
    │                   │       ├── server.go      Fulcro server
    │                   │       └── utils.go       Utility functions
    │                   └── main.go                Server main
    └── main
        ├── config                                 Clojure server runtime configuration
        │   ├── defaults.edn
        │   ├── dev.edn
        │   └── prod.edn
        └── org
            └── edgexfoundry
                └── ui
                    └── manager
                        ├── api
                        │   ├── edgex.clj            EdgeX REST API functions (*)
                        │   ├── file_db.clj          file upload support (*)
                        │   ├── mutations.clj        server-side version of mutations (*)
                        │   ├── mutations.cljs       client-side version of mutations
                        │   ├── read.clj             server implementation of reads (*)
                        │   └── util.cljc            common utility functions
                        ├── client.cljs              client creation (shared among dev/prod)
                        ├── client_main.cljs         production client main
                        ├── development_preload.cljs development mode extra initialisation
                        ├── server.clj               server creation (shared among dev/prod) (*)
                        ├── server_main.clj          production server main (*)
                        └── ui
                            ├── addressables.cljs      Addressable table and editing
                            ├── commands.cljs          Command support
                            ├── common.cljs            Common ui functions and constants
                            ├── date_time_picker.cljs  Date and Time picker component
                            ├── devices.cljs           Device table
                            ├── dialogs.cljs           Common modal dialog functions
                            ├── endpoints.cljs         Service endpoint editing
                            ├── exports.cljs           Exports table and editing
                            ├── graph.cljs             Graphing functions
                            ├── ident.cljc             Identity function
                            ├── labels.cljs            Form labeling
                            ├── logging.cljs           Log entry display
                            ├── main.cljs              Main page
                            ├── notifications.cljs     Notification display
                            ├── profiles.cljs          Profile tables and editing
                            ├── readings.cljs          Readings table
                            ├── root.cljs              UI root component
                            ├── routing.cljs           HTML routing support
                            ├── schedules.cljs         Schedule tables and editing
                            └── table.cljc             Table macro and utility functions

(*) Java based Clojure server is deprecated and left in place for maintenance of Fulcro server compatibility.
```

## Development Mode

## Setting Up

The shadow-cljs compiler uses all cljsjs and NPM js dependencies through
NPM. If you use a library that is in cljsjs you will also have to add
it to your `package.json`.

You also cannot compile this project until you install the ones it
depends on already:

```
$ npm install
```

or if you prefer `yarn`:

```
$ yarn install
```

Adding NPM Javascript libraries is as simple as adding them to your
`package.json` file and requiring them! See the
[the Shadow-cljs User's Guide](https://shadow-cljs.github.io/docs/UsersGuide.html#_javascript)
for more information.

## Development Mode

Shadow-cljs handles the client-side development build. The file
`src/main/org/edgexfoundry/ui/manager/client.cljs` contains the code to start and refresh
the client for hot code reload.

In general it is easiest just to run the compiler in server mode:

```
$ npx shadow-cljs server
INFO: XNIO version 3.3.8.Final
Nov 10, 2018 8:08:23 PM org.xnio.nio.NioXnio <clinit>
INFO: XNIO NIO Implementation Version 3.3.8.Final
shadow-cljs - HTTP server for :test available at http://localhost:8022
shadow-cljs - HTTP server for :workspaces available at http://localhost:8023
shadow-cljs - server version: 2.7.2
shadow-cljs - server running at http://localhost:9630
shadow-cljs - socket REPL running on port 51936
shadow-cljs - nREPL server started on port 9000
...
```

then *navigate to the server URL* (shown in this example as http://localhost:9630) and
use the *Builds* menu to enable/disable whichever builds you want watched/running.

Shadow-cljs will also start a web server for any builds that configure one.

### Start the Go Web server

```
$ cd src/go
$ export GOPATH=$PWD
$ cd src/github.com/edgexfoundry/go-ui-server/
```
#### Install the Go packages
```
$ go get
```
#### Make soft link and start the web server
```
$ ln -s ../../../../../../resources/public/ assets; ln -s ../../../../res/
$ go run main.go
```
#### Log in
Navigate to http://localhost:3001 to login.
The default password is `admin`.
User can change the password by clicking the `Change password` link.

### Client REPL

The shadow-cljs compiler starts an nREPL. It is configured to start on
port 9000 (in `shadow-cljs.edn`).

In IntelliJ: add a *remote* Clojure REPL configuration with
host `localhost` and port `9000`.

then something like:

```
(shadow/nrepl-select :main)
```

will connect you to the REPL for a specific build (NOTE: Make sure you have
a browser running the result, or your REPL won't have anything to talk to!)

If you're using CIDER
see [the Shadow-cljs User's Guide](https://shadow-cljs.github.io/docs/UsersGuide.html#_cider)
for more information.

### The API Server

In order to work with your main application you'll want to
start your own server that can also serve your application's API.

Start a LOCAL clj nREPL in IntelliJ, or from the command line:

```bash
$ lein repl
user=> (start)
user=> (stop)
...
user=> (restart) ; stop, reload server code, and go again
user=> (tools-ns/refresh) ; retry code reload if hot server reload fails
```

The URL to work on your application is then
[http://localhost:3000](http://localhost:3000).

Hot code reload, preloads, and such are all coded into the javascript.

### Preloads

There is a preload file that is used on the development build of the
application `org.edgexfoundry.ui.manager.development-preload`. You can add code here that
you want to execute before the application initializes in development
mode.

### Fulcro Inspect

Fulcro inspect will preload on the development build of the main
application and workspaces.  You must install the plugin in Chrome from the
Chrome store (free) to access it.  It will add a Fulcro Inspect tab to the
developer tools pane.
