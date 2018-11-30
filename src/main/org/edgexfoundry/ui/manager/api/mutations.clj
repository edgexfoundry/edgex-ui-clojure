;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.mutations
  (:require
    [taoensso.timbre :as timbre]
    [clj-http.client :as http]
    [org.edgexfoundry.ui.manager.api.edgex :as e]
    [org.edgexfoundry.ui.manager.api.file-db :as file-db]
    [org.edgexfoundry.ui.manager.api.util :as u]
    [fulcro.client.primitives :as prim]
    [fulcro.server :as core :refer [defmutation]]))

(defmutation update-lock-mode
             [{:keys [id mode]}]
             (action [{:keys [state]}]
                     (let [id-str (e/key-to-string id)
                           mode-str (e/key-to-string mode)]
                       (timbre/info "set admin state: device" id "mode" mode)
                       (http/put (str "http://" (:command @e/endpoints)
                                      "/api/v1/device/" id-str "/adminstate/" mode-str))
                       id)))

(defmutation upload-profile
             [{:keys [file-id]}]
             (action [{:keys [state]}]
                     (timbre/info "upload profile id" file-id (file-db/get-file file-id))
                     (http/post (str "http://" (:metadata @e/endpoints) "/api/v1/deviceprofile/uploadfile")
                                {:multipart [{:name "Content/type" :content "application/x-yaml"}
                                             {:name "file" :content (file-db/get-file file-id)}]})
                     (file-db/delete-file file-id)))

(defmutation add-device
  "Add device"
  [{:keys [name description labels profile-name service-name addressable-name]}]
  (action [{:keys [state]}]
          (let [device {:name name
                        :description description
                        :labels labels
                        :profile {:name profile-name}
                        :service {:name service-name}
                        :addressable {:name addressable-name}
                        :adminState "UNLOCKED"
                        :operatingState "ENABLED"}]
            (timbre/info "add device" name description labels profile-name service-name addressable-name)
            (e/edgex-post :metadata "device" device))))

(defmutation add-addressable
  [{:keys [tempid name protocol address port path method publisher topic user password]}]
  (action [{:keys [state]}]
          (let [addressable {:name name
                             :address address
                             :protocol protocol
                             :port port
                             :path path
                             :method (case method
                                       :get "GET"
                                       :post "POST"
                                       :put "PUT"
                                       :delete "DELETE")
                             :publisher publisher
                             :topic topic
                             :user user
                             :password password}]
            (timbre/info "add addressable" addressable)
            {::prim/tempids {tempid (-> (e/edgex-post :metadata "addressable" addressable) :body keyword)}})))

(defmutation edit-addressable
  [{:keys [id protocol address port path method publisher topic user password]}]
  (action [{:keys [state]}]
          (let [a {:id (e/key-to-string id)
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
                   :user user}]
            (timbre/info "edit addressable" a)
            (e/edgex-put :metadata "addressable" a)
            id)))

(defmutation delete-device
  [{:keys [id]}]
  (action [{:keys [state]}]
          (timbre/info "delete device" id)
          (->> id
               e/key-to-string
               (str "device/id/")
               (e/edgex-delete :metadata))))

(defmutation add-schedule
  [{:keys [tempid name start end frequency run-once]}]
  (action [{:keys [state]}]
          (let [schedule {:name name
                          :start start
                          :end end
                          :frequency frequency
                          :runOnce run-once}]
            (timbre/info "add schedule" name start end)
            {::prim/tempids {tempid (-> (e/edgex-post :metadata "schedule" schedule) :body keyword)}})))

(defmutation delete-schedule
  [{:keys [id]}]
  (action [{:keys [state]}]
          (timbre/info "delete schedule" id)
          (->> id
               e/key-to-string
               (str "schedule/id/")
               (e/edgex-delete :metadata))))

(defmutation add-schedule-event
  [{:keys [tempid name parameters schedule-name service-name addressable-name]}]
  (action [{:keys [state]}]
          (let [event {:name name
                       :addressable {:name addressable-name}
                       :parameters parameters
                       :schedule schedule-name
                       :service service-name}]
            (timbre/info "add schedule event" name service-name)
            {::prim/tempids {tempid (-> (e/edgex-post :metadata "scheduleevent" event) :body keyword)}})))

(defmutation delete-schedule-event
  [{:keys [id]}]
  (action [{:keys [state]}]
          (timbre/info "delete schedule event" id)
          (->> id
               e/key-to-string
               (str "scheduleevent/id/")
               (e/edgex-delete :metadata))))

(defmutation delete-profile
  [{:keys [id]}]
  (action [{:keys [state]}]
          (timbre/info "delete profile" id)
          (->> id
               e/key-to-string
               (str "deviceprofile/id/")
               (e/edgex-delete :metadata))))

(defmutation delete-addressable
  [{:keys [id]}]
  (action [{:keys [state]}]
          (timbre/info "delete addressable" id)
          (->> id
               e/key-to-string
               (str "addressable/id/")
               (e/edgex-delete :metadata))))

(defmutation issue-set-command
  [{:keys [url values]}]
  (action [{:keys [state] :as env}]
          (e/edgex-put-command url values)))

(defmutation add-export
  [{:keys [tempid name addressable format destination compression encryptionAlgorithm encryptionKey initializingVector
           enable]}]
  (action [{:keys [state]}]
          (let [ex (u/mk-export addressable format destination compression encryptionAlgorithm encryptionKey
                                initializingVector enable)
                data (assoc ex :name name)]
            (timbre/info "add export" name ex)
            {::prim/tempids {tempid (->>
                                      data
                                      (e/edgex-post :export "registration")
                                      :body
                                      keyword)}})))

(defmutation edit-export
  [{:keys [id addressable format destination compression encryptionAlgorithm encryptionKey initializingVector
           enable]}]
  (action [{:keys [state]}]
          (let [ex (u/mk-export addressable format destination compression encryptionAlgorithm encryptionKey
                                initializingVector enable)
                data (assoc ex :id id)]
            (e/edgex-put :export "registration" data)
            id)))

(defmutation delete-export
  [{:keys [id]}]
  (action [{:keys [state]}]
          (->> id
               e/key-to-string
               (str "registration/id/")
               (e/edgex-delete :export))))

(defmutation save-endpoints [{:keys [metadata data command logging notifications export]}]
  (action [{:keys [state]}]
          (swap! e/endpoints merge {:metadata metadata
                                    :export export
                                    :data data
                                    :command command
                                    :logging logging
                                    :notifications notifications})))
