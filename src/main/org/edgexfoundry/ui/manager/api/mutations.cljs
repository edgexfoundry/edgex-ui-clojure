;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.mutations
  (:require
    [fulcro.client.mutations :refer [defmutation]]
    [fulcro.ui.bootstrap3 :as b]
    [org.edgexfoundry.ui.manager.ui.common :as co]
    [org.edgexfoundry.ui.manager.api.util :as u]
    [cljs-time.coerce :as ct]
    [cljs-time.format :as ft]))

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
  [{:keys [tempid name parameters schedule-name service-name addressable-name]}]
  (action [{:keys [state]}]
          (let [filter-by-name (fn [list] (filterv #(= (:name %) addressable-name) list))
                get-addr (fn [s] (-> s :addressable vals filter-by-name first))
                e {:created    0
                   :id         tempid
                   :modified   0
                   :name       name
                   :parameters parameters
                   :origin     0
                   :schedule   schedule-name
                   :service    service-name
                   :type       :schedule-event}]
            (swap! state (fn [s] (let [se (assoc e :addressable (get-addr s))
                                       add-ref #(conj % [:schedule-event tempid])]
                                   (-> s
                                       (assoc-in [:schedule-event tempid] se)
                                       (update-in [:show-schedule-events :singleton :content] add-ref)))))))
  (remote [env] true))

(defmutation add-schedule
  [{:keys [tempid name start end frequency run-once]}]
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

(defmutation add-export
  [{:keys [tempid name addressable format destination compression encryptionAlgorithm encryptionKey initializingVector
           enable]}]
  (action [{:keys [state]}]
          (let [addr (select-keys addressable [:address :method :name :origin :password :path :port :protocol
                                               :publisher :topic :user])
                ex (-> (u/mk-export addr format destination compression encryptionAlgorithm encryptionKey
                                    initializingVector enable)
                       (merge {:id tempid
                               :type :export
                               :name name}))
                add-ref #(conj % [:export tempid])]
            (swap! state (fn [s] (-> s
                                     (assoc-in [:export tempid] ex)
                                     (update-in [:show-exports :singleton :content] add-ref))))))
  (remote [env] true))

(defmutation edit-export
  [{:keys [id addressable format destination compression encryptionAlgorithm encryptionKey initializingVector
           enable]}]
  (action [{:keys [state]}]
          (let [addr (select-keys addressable [:address :method :name :origin :password :path :port :protocol
                                               :publisher :topic :user])
                ex (-> (u/mk-export addr format destination compression encryptionAlgorithm encryptionKey
                                    initializingVector enable)
                       (merge {:id id
                               :type :export}))]
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
