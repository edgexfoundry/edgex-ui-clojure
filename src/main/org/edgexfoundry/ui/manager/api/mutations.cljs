;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.mutations
  (:require
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.ui.bootstrap3 :as b]
    [org.edgexfoundry.ui.manager.ui.common :as co]
    [org.edgexfoundry.ui.manager.ui.load :as ld]
    [org.edgexfoundry.ui.manager.ui.routing :as r]
    [org.edgexfoundry.ui.manager.api.util :as u]
    [cljs-time.coerce :as ct]
    [cljs-time.format :as ft]
    [goog.net.cookies :as cks]
    [pushy.core :as pushy]))

(defmutation login-complete
  "Fulcro mutation: Attempted login post-mutation the update the UI with the result. Requires the app-root of the mounted application
  so routing can be started."
  [{:keys [app-root]}]
  (action [{:keys [component state]}]
          ; idempotent (start routing)
          (when app-root
            (r/start-routing app-root))
          (swap! state (fn [s] (let [session (get-in s (conj co/login-page-ident :session_id))]
                                 (cks/set "EDGEX_SESSION_ID" session 3600)
                                 (-> s
                                   (assoc-in (conj co/login-page-ident :ui/password) "")
                                   (assoc :pw-updated? false :fulcro/server-error nil)))))
          (r/nav-to! component :main)))

(defmutation logout
  "Fulcro mutation: Removes user identity from the local app and asks the server to forget the user as well."
  [p]
  (action [{:keys [component state]}]
          (when (and @r/use-html5-routing @r/history)
            (pushy/set-token! @r/history "/login"))
          (cks/remove "EDGEX_SESSION_ID")))

(defmutation upload-profile
  "Upload profile"
  [{:keys [file-id]}]
  (remote [env] true))

(defmutation add-device
  "Add device"
  [{:keys [name description labels profile-name service-name addressable-name]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (assoc-in [:new-device :singleton :confirm?] false)))))
  (remote [env] true))

(defmutation add-addressable
  [{:keys [tempid name protocol address port path method publisher topic user password]}]
  (action [{:keys [state]}]
          (let [method-str (case method
                             :get "GET"
                             :post "POST"
                             :put "PUT"
                             :delete "DELETE")
                url (str protocol "://" address ":" port path)
                a {:created 0
                   :id tempid
                   :address address
                   :baseURL nil
                   :method method-str
                   :modified 0
                   :name name
                   :origin 0
                   :password password
                   :path path
                   :port port
                   :protocol protocol
                   :publisher publisher
                   :topic topic
                   :url url
                   :user user
                   :type :addressable}]
            (swap! state (fn [s] (let [add-ref #(conj % [:addressable tempid])]
                                   (-> s
                                       (assoc-in [:addressable tempid] a)
                                       (update-in [:show-addressables :singleton :content] add-ref)
                                       (update-in [:new-export :singleton :addressables] add-ref)))))))
  (remote [env] true))

(defmutation edit-addressable
  [{:keys [id protocol address port path method publisher topic user password]}]
  (action [{:keys [state]}]
          (let [url (str protocol "://" address ":" port path)
                a {:id id
                   :address address
                   :method (case method
                             :get "GET"
                             :post "POST"
                             :put "PUT"
                             :delete "DELETE")
                   :password password
                   :path path
                   :port port
                   :protocol protocol
                   :publisher publisher
                   :topic topic
                   :url url
                   :user user}]
            (swap! state (fn [s] (-> s
                                     (update-in [:addressable id] merge a))))))
  (remote [env] true))

(defmutation update-lock-mode
  "mutation: Update admin status"
  [{:keys [id mode]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (assoc-in s [:device id :adminState] mode))))
  (remote [env] true))

(defmutation issue-set-command
  [{:keys [url values]}]
  (remote [env] true))

(defmutation delete-device
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-device [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :device dissoc id)
                                     (update-in (conj co/device-list-ident :content) filter-device))))))
  (remote [env] true))

(defmutation delete-schedule
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-schedule [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :schedule dissoc id)
                                     (update-in (conj co/schedules-list-ident :content) filter-schedule))))))
  (remote [env] true))

(defmutation add-schedule-event
  [{:keys [tempid name parameters schedule-name target protocol httpMethod address port path publisher topic user password]}]
  (action [{:keys [state]}]
          (let [e {:created    0
                   :id         tempid
                   :modified   0
                   :name       name
                   :parameters parameters
                   :origin     0
                   :interval   schedule-name
                   :target     target
                   :protocol   protocol
                   :httpMethod (co/conv-http-method httpMethod)
                   :address    address
                   :port       port
                   :path       path
                   :publisher  publisher
                   :topic      topic
                   :user       user
                   :password   password
                   :type       :schedule-event}]
            (swap! state (fn [s] (let [add-ref #(conj % [:schedule-event tempid])]
                                   (-> s
                                       (assoc-in [:schedule-event tempid] e)
                                       (update-in [:show-schedule-events :singleton :content] add-ref)))))))
  (remote [env] true))

(defmutation add-schedule
  [{:keys [tempid name start end frequency cron run-once]}]
  (action [{:keys [state]}]
          (let [sc {:created    0
                    :cron       nil
                    :frequency  frequency
                    :id         tempid
                    :modified   0
                    :name       name
                    :origin     0
                    :runOnce    run-once
                    :type       :schedule}
                add-ref #(conj % [:schedule tempid])
                assoc-if (fn [m k v] (if v (assoc m k v) m))
                sc (-> sc (assoc-if :start start) (assoc-if :end end))]
            (swap! state (fn [s] (-> s
                                     (assoc-in [:schedule tempid] sc)
                                     (update-in [:show-schedules :singleton :content] add-ref))))))
  (remote [env] true))

(defmutation add-notification
  [{:keys [tempid slug description sender category severity content labels]}]
  (action [{:keys [state]}]
          (let [notifyObj {:id tempid
                           :type :notification
                           :slug slug
                           :description description
                           :sender sender
                           :category category
                           :severity severity
                           :content content
                           :labels labels}
                add-ref #(conj % [:notification tempid])]
            (swap! state (fn [s] (-> s
                                     (assoc-in [:notification tempid] notifyObj)
                                     (update-in [:show-notifications :singleton :content] add-ref))))))
  (remote [env] true))

(defmutation delete-notification
  [{:keys [id slug]}]
  (action [{:keys [state]}]
          (letfn [(filter-notification [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :subscription dissoc id)
                                     (update-in (conj co/notifications-list-ident :content) filter-notification))))))
  (remote [env] true))

(defmutation add-subscription
  [{:keys [tempid] :as sub}]
  (action [{:keys [state]}]
          (let [add-ref #(conj % [:subscription tempid])
                subObj (merge sub {:id tempid :type :subscription})]
            (swap! state (fn [s] (-> s
                                     (assoc-in [:subscription tempid] subObj)
                                     (update-in [:show-subscriptions :singleton :content] add-ref))))))
  (remote [env] true))

(defmutation edit-subscription
  [{:keys [id] :as sub}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (assoc-in [:subscription id] sub)))))
  (remote [env] true))

(defmutation delete-subscription
  [{:keys [id slug]}]
  (action [{:keys [state]}]
          (letfn [(filter-subscription [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :subscription dissoc id)
                                     (update-in (conj co/subscriptions-list-ident :content) filter-subscription))))))
  (remote [env] true))

(defmutation add-export
  [{:keys [tempid name format destination compression encryptionAlgorithm encryptionKey initializingVector
           device-filter reading-filter enable] :as exp}]
  (action [{:keys [state]}]
          (let [addr (select-keys exp [:address :method :origin :password :path :port :protocol :publisher :topic :user :cert
                                       :key])
                ex (-> (u/mk-export addr format destination compression encryptionAlgorithm encryptionKey
                                    initializingVector device-filter reading-filter enable)
                       (merge {:id tempid
                               :type :export
                               :name name}))
                add-ref #(conj % [:export tempid])]
            (swap! state (fn [s] (-> s
                                     (assoc-in [:export tempid] ex)
                                     (update-in [:show-exports :singleton :content] add-ref))))))
  (remote [env] true))

(defmutation edit-export
  [{:keys [id name format destination compression encryptionAlgorithm encryptionKey initializingVector enable
           device-filter reading-filter] :as exp}]
  (action [{:keys [state]}]
          (let [addr (select-keys exp [:address :method :origin :password :path :port :protocol :publisher :topic :user :cert
                                       :key])
                map-method #(case %
                              :post "POST"
                              :put "PUT"
                              :delete "DELETE"
                              "GET")
                ex (-> (u/mk-export (update addr :method map-method) format destination compression encryptionAlgorithm encryptionKey
                                    initializingVector device-filter reading-filter enable)
                       (merge {:id id
                               :name name
                               :type :export
                               :method (map-method (:method addr))}))]
            (swap! state (fn [s] (-> s
                                     (update-in [:export id] merge ex))))))
  (remote [env] true))

(defmutation delete-schedule-event
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-schedule-event [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :schedule-event dissoc id)
                                     (update-in (conj co/schedule-events-list-ident :content)
                                                filter-schedule-event))))))
  (remote [env] true))

(defmutation delete-profile
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-profile [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :device-profile dissoc id)
                                     (update-in (conj co/profile-list-ident :content) filter-profile))))))
  (remote [env] true))

(defmutation delete-addressable
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-addressable [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :addressable dissoc id)
                                     (update-in (conj co/addressable-list-ident :content) filter-addressable))))))
  (remote [env] true))

(defmutation delete-export
  [{:keys [id]}]
  (action [{:keys [state]}]
          (letfn [(filter-export [list] (filterv #(not= (second %) id) list))]
            (swap! state (fn [s] (-> s
                                     (update :export dissoc id)
                                     (update-in (conj co/exports-list-ident :content) filter-export))))))
  (remote [env] true))

(defmutation save-endpoints [{:keys [metadata data command logging notifications export]}]
  (remote [env] true))
