;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.routing
  (:require
    [fulcro.client.routing :as r]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [pushy.core :as pushy]
    [sibiro.core :as sibiro]
    [fulcro.client.primitives :as prim]
    [fulcro.client.logging :as log]
    [org.edgexfoundry.ui.manager.ui.common :as co]))

(def app-routing-tree
  (r/routing-tree
    (r/make-route :main [(r/router-instruction :device-router co/device-list-ident)])
    (r/make-route :info [(r/router-instruction :device-router [:device :param/id])])
    (r/make-route :control [(r/router-instruction :device-router co/command-list-ident)])
    (r/make-route :reading [(r/router-instruction :device-router co/reading-page-ident)])
    (r/make-route :profile [(r/router-instruction :device-router co/profile-list-ident)])
    (r/make-route :schedule [(r/router-instruction :device-router co/schedules-list-ident)])
    (r/make-route :schedule-event [(r/router-instruction :device-router co/schedule-events-list-ident)])
    (r/make-route :schedule-event-info [(r/router-instruction :device-router [:schedule-event :param/id])])
    (r/make-route :profile-yaml [(r/router-instruction :device-router co/profile-yaml-ident)])
    (r/make-route :addressable [(r/router-instruction :device-router co/addressable-list-ident)])
    (r/make-route :logs [(r/router-instruction :device-router co/log-entry-list-ident)])
    (r/make-route :notification [(r/router-instruction :device-router co/notifications-list-ident)])
    (r/make-route :export [(r/router-instruction :device-router co/exports-list-ident)])))

(def valid-handlers (-> (get app-routing-tree r/routing-tree-key) keys set))

;; To keep track of the global HTML5 pushy routing object
(def history (atom nil))

;; To indicate when we should turn on URI mapping. This is so you can use with devcards (by turning it off)
(defonce use-html5-routing (atom true))

(def app-routes
  [[:get "/" :main]
   [:get "/info/:id{[0-9a-f]+}" :info]
   [:get "/command" :control]
   [:get "/reading" :reading]
   [:get "/profile" :profile]
   [:get "/schedule" :schedule]
   [:get "/schedule-event" :schedule-event]
   [:get "/schedule-event-info/:id{[0-9a-f]+}" :schedule-event-info]
   [:get "/profile-yaml" :profile-yaml]
   [:get "/addressable" :addressable]
   [:get "/log" :logs]
   [:get "/notification" :notification]
   [:get "/export" :export]])

(def compiled-routes (sibiro/compile-routes app-routes))

(defn invalid-route?
  "Returns true if the given keyword is not a valid location in the routing tree."
  [kw]
  (not (contains? valid-handlers kw)))

(defn redirect*
  "Use inside of mutations to generate a URI redirect to a different page than you are on. Honors setting of use-html5-history.
  Use the plain function `nav-to!` for UI-level navigation."
  [state-map {:keys [handler route-params] :as sibiro-match}]
  (if @use-html5-routing
    (let [path (sibiro/uri-for compiled-routes handler route-params)]
      (pushy/set-token! @history path)
      state-map)
    (r/update-routing-links state-map sibiro-match)))

(defn set-route!*
  "Implementation of choosing a particular sibiro match. Used internally by the HTML5 history event implementation.
  Updates the UI only, unless the URI is invalid, in which case it redirects the UI and possibly generates new HTML5
  history events."
  [state-map {:keys [handler route-params] :as sibiro-match}]
  (let [rp (into {} (for [[k v] route-params] [k (keyword v)]))
        sm {:handler handler :route-params rp}]
    (cond
      ;(or (= :new-user handler) (= :login handler)) (r/update-routing-links state-map sibiro-match)
      ;(not (:logged-in? state-map)) (-> state-map
      ;                                  (assoc :loaded-uri (when @history #?(:clj "/" (pushy/get-token @history)))
      ;                                  (redirect* {:handler :login}))
      (invalid-route? handler) (redirect* state-map {:handler :main})
      :else (-> state-map
                (r/update-routing-links sm)
                (assoc-in (conj co/main-page-ident :ui/route-handler) handler)))))

(defmutation set-route!
  "Om mutation: YOU PROBABLY DO NOT WANT THIS ONE. Use `nav-to!` (as a plain function from the UI) instead.

   Set the route to the given sibiro match. Checks to make sure the user is allowed to do so (are
   they logged in?). Sends them to the login screen if they are not logged in. This does NOT update the URI."
  [sibiro-match]
  (action [{:keys [state]}] (swap! state set-route!* sibiro-match)))

(defn key-to-string [k]
  (subs (str k) 1))

(defn nav-to!
  "Run a navigation mutation from the UI, but make sure that HTML5 routing is correctly honored so the components can be
  used as an app or in devcards. Use this in nav UI links instead of href or transact. "
  ([component page] (nav-to! component page {}))
  ([component page route-params]
   (let [rp (into {} (for [[k v] route-params] [k (key-to-string v)]))]
     (if (and @history @use-html5-routing)
       (let [path (:uri (sibiro/uri-for compiled-routes page rp))]
         (pushy/set-token! @history path))
       (prim/transact! component `[(set-route! ~{:handler page :route-params route-params}) :device-page])))))

(defn match-uri [uri]
  (let [match (sibiro/match-uri compiled-routes uri :get)]
    {:handler (:route-handler match) :route-params (:route-params match)}))

(defn start-routing [app-root]
  (when (and @use-html5-routing (not @history))
    (let [; NOTE: the :pages follow-on read, so the whole UI updates when page changes
          set-route! (fn [match]
                       ; Delay. history events should happen after a tx is processed, but a set token could happen during.
                       ; Time doesn't matter. This thread has to let go of the CPU before timeouts can process.
                       (js/setTimeout #(prim/transact! app-root `[(set-route! ~match) :device-page]) 10))]
      (reset! history (pushy/pushy set-route! match-uri))
         (pushy/start! @history))))
