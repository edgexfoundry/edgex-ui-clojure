;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.server
  (:require
    [fulcro.server :as core]
    [com.stuartsierra.component :as component]
    [org.httpkit.server :refer [run-server]]
    [fulcro.server :as server]
    [fulcro.client :as fc]
    [taoensso.timbre :as timbre]
    [org.edgexfoundry.ui.manager.api.read]
    [org.edgexfoundry.ui.manager.api.mutations]
    [org.edgexfoundry.ui.manager.api.file-db :as file-db]
    [org.edgexfoundry.ui.manager.api.edgex :as e]
    [bidi.bidi :as bidi]
    [fulcro.ui.file-upload :as upload]

    [ring.middleware.session :as session]
    [ring.middleware.session.store :as store]
    [ring.middleware.resource :as resource]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.multipart-params :refer [wrap-multipart-params]]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.middleware.cookies :as cookies]
    [ring.util.request :as req]
    [ring.util.response :refer [resource-response]]
    [fulcro.util :as util]
    [fulcro.server-render :as ssr]
    [clojure.string :as str]
    [fulcro.i18n :as i18n]
    [fulcro.client.mutations :as m]
    [fulcro.client.dom :as dom]))

; To handle the end of the processing chain.
(defn not-found [req]
  {:status  404
   :headers {"Content-Type" "text/plain"}
   :body    "Resource not found."})

(defrecord APIModule []
  server/Module
  (system-key [this] :api-module)          ; this module will be known in the component system as :api-module. Allows you to inject the module.
  (components [this] {})                   ; Additional components to build. This allows library modules to inject other dependencies that it needs into the system. Commonly empty.
  server/APIHandler
  (api-read [this] core/server-read)       ; read/mutate handlers (as function) Here we're using the fulcro multimethods so defmutation et al will work.
  (api-mutate [this] core/server-mutate))

(defn index [req]
  (assoc (resource-response (str "index.html") {:root "public"})
    :headers {"Content-Type" "text/html"}))

(def default-routes
  ["" {"/" :index
       ["/info/" :id] :index
       "/command" :index
       "/reading" :index
       "/profile" :index
       "/schedule" :index
       "/profile-yaml" :index
       "/addressable":index
       "/log" :index
       "/notification" :index
       "/export" :index}])

(defn route-handler [req]
  (let [match  (bidi/match-route default-routes (:uri req)
                                 :request-method (:request-method req))]
    (case (:handler match)
      ;; explicit handling of / as index.html. wrap-resources does the rest
      :index (index req)
      nil)))

(defn wrap-connection
  "Ring middleware function that maps / to index.html"
  [handler route-handler]
  (fn [req]
    (or (route-handler req)
        (handler req))))


; A component that creates the full server middleware and stores it under the key :full-server-middleware (used by the web server).
; Your modules (e.g. APIModule above) are composed into one api-handler function by the fulcro-system function, which in
; turn is placed in the component system  under the ; key :fulcro.server/api-handler.
; The :fulcro.server/api-handler component in turn has a key :middleware whose value is the middleware for handling API requests from the client.
; So, you a component (CustomMiddleware) that composes *that* API middleware function
; into the larger whole. In our application our full-server-middleware needs the user
; database and session store (which are injected).
(defrecord CustomMiddleware [full-server-middleware api-handler upload rest]
  component/Lifecycle
  (stop [this] (dissoc this :full-server-middleware))
  (start [this]
    (let [wrap-api (:middleware api-handler)]
      ; The chained middleware function needs to be *stored* at :full-server-middleware,
      ; because we're using a Fulcro web server and it expects to find it there.
      (assoc this :full-server-middleware
                  (-> not-found
                      (wrap-resource "public")
                      (wrap-connection route-handler)
                      wrap-api                                ; from fulcro-system modules. Handles /api
                      (upload/wrap-file-upload upload)
                      core/wrap-transit-params
                      core/wrap-transit-response
                      wrap-content-type
                      wrap-not-modified
                      wrap-params
                      wrap-multipart-params
                      wrap-gzip)))))

(def http-kit-opts
  [:ip :port :thread :worker-name-prefix
   :queue-size :max-body :max-line])

; A component that creates a web server and hooks lifecycle up to it. The server-middleware-component (CustomMiddleware)
; and config are injected.
(defrecord WebServer [config ^CustomMiddleware server-middleware-component server port]
  component/Lifecycle
  (start [this]
    (try
      (let [config         (:value config)                  ; config is a component, must pull out value
            server-opts    (select-keys config http-kit-opts)
            port           (:port server-opts)
            middleware     (:full-server-middleware server-middleware-component)
            started-server (run-server middleware server-opts)]
        (timbre/info (str "Web server (http://localhost:" port ")") "started successfully. Config of http-kit options:" server-opts)
        (assoc this :port port :server started-server))
      (catch Exception e
        (timbre/fatal "Failed to start web server " e)
        (throw e))))
  (stop [this]
    (if-not server this
                   (do (server)
                       (timbre/info "web server stopped.")
                       (assoc this :server nil)))))

(defrecord PretendFileUpload []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  upload/IFileUpload
  (upload-prefix [this] "/file-upload")
  (is-allowed? [this request] true)
  (store [this file] (file-db/save-file file))
  (retrieve [this id] (file-db/get-file id))
  (delete [this id] (file-db/delete-file id)))

(defrecord RESTConnector [config]
  component/Lifecycle
  (start [this]
    (let [config (:value config)
          endpoints (:endpoints config)]
      (timbre/info "endpoints" endpoints)
      (e/set-endpoints! endpoints)
      this))
  (stop [this] this))

; Injection configuration time! The Stuart Sierra component system handles all of the injection. You simple create
; components, place them into the system under a key, and wrap them with (component/using ...) in order to specify what they need.
; When the system is started, the dependencies will be analyzed and started in the correct order (leaves to tree top).
(defn make-system
  "Builds the server component system, which can then be started/stopped. `config-path` is a relative or absolute path
  to a web server configuration EDN file."
  [config-path]
  (core/fulcro-system
    {:components {:config                      (core/new-config config-path) ; you MUST use config if you use our server
                  :upload                      (map->PretendFileUpload {})
                  :rest                        (component/using
                                                 (map->RESTConnector {})
                                                 [:config])
                  ; Creation/injection of the middleware stack
                  :server-middleware-component (component/using
                                                 (map->CustomMiddleware {})
                                                 ; the middleware needs the composed module's api handler, session store, and user database
                                                 {:api-handler   :fulcro.server/api-handler
                                                  :upload :upload
                                                  :rest :rest
                                                  }) ; remap the generated api handler's key to api-handler, so it is easier to use there
                  ; The web server itself, which needs the config and full-stack middleware.
                  :web-server                  (component/using (map->WebServer {})
                                                                [:config :server-middleware-component])}
     ; Modules are composable into the API handler (each can have their own read/mutate) and
     ; are joined together in a chain that is injected into the component system as :fulcro.server/api-handler
     :modules    [(component/using (map->APIModule {})
                                   ; the things injected here will be available in the modules' parsing env
                                   [:upload])]}))
