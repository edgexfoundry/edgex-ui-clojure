;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.read
  (:require
    [clojure.set :as set]
    [fulcro.server :refer [defquery-entity defquery-root]]
    [taoensso.timbre :as timbre]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clj-time.coerce :as co]
    [clj-time.format :as ft]
    [clj-time.core :as tc]
    [org.edgexfoundry.ui.manager.api.edgex :as e]))

(defn edgex-get-resources [devices]
  (let [get-reendpoints (fn [d] (e/edgex-get :data (str "reading/device/" (:name d) "/100") e/convert-resource))]
    (into [] (mapcat get-reendpoints devices))))

(defn getfn [gd]
  (let [result (->> gd :url http/get :body json/read-str (into []))]
    result))

(defn edgex-get-device-commands [id]
  (let [commands (mapv e/convert-command (get (e/edgex-get-path :command (str "device/" (subs (str id) 1))) "commands"))
        apply-get #(if (-> % :get :responses)
                     (update % :get getfn)
                     (assoc % :get [[(:name %) "N/A"]]))
        add-value #(-> % apply-get (set/rename-keys {:get :value}))
        split-values (fn [cd] (let [v (:value cd)
                                    c (count v)]
                                    (map-indexed (fn [idx item]
                                                   (-> cd (merge {:value item :pos idx :size c}))) v)))
                      result (->> commands (mapv add-value) (mapcat split-values) vec)]
    result))

(defn get-readings-in-time-range [ids coll start end limit]
  (let [readings (e/edgex-get :data (str "reading/" start "/" end "/100") #(e/convert-basic % :reading))
        new-readings (filterv #(-> % :id ids not) readings)
        new-ids (set/union ids (into #{} (map :id new-readings)))
        new-coll (concat coll new-readings)]
    (if (or (< (count readings) 100) (<= limit 0))
      new-coll
      (recur new-ids new-coll (-> new-readings peek :created) end (dec limit)))))

(defn edgex-get-device-readings [name from to]
  (let [readings (get-readings-in-time-range #{} [] from to 99)]
    (filterv #(= (:device %) name) readings)))

(defn edgex-get-profile-yaml [id]
  (let [id-str (e/key-to-string id)]
    [{:yaml (e/edgex-get-path-raw :metadata (str "/deviceprofile/yaml/" id-str))}]))

(defquery-root :q/login
  (value [env params]
         (timbre/log "attempt to login")
         (try
           (catch Exception ex
             (throw (ex-info "Server error" {:type :fulcro.client.primitives/abort :status 401 :body "Unauthorized User"}))))))

(defquery-root :q/edgex-devices
               (value [env params]
                      (e/edgex-get :metadata "device" e/convert-device)))

(defquery-root :q/edgex-device-services
               (value [env params]
                      (e/edgex-get :metadata "deviceservice" e/convert-device-service)))

(defquery-root :q/edgex-schedule-events
               (value [env params]
                      (e/edgex-get :metadata "scheduleevent" e/convert-schedule-event)))

(defquery-root :q/edgex-addressables
               (value [env params]
                      (e/edgex-get :metadata "addressable" e/convert-addressable)))

(defquery-root :q/edgex-profiles
               (value [env params]
                      (e/edgex-get :metadata "deviceprofile" e/convert-device-profile)))

(defquery-root :q/edgex-commands
               (value [env params]
                      (edgex-get-device-commands (:id params))))

(defquery-root :q/edgex-readings
               (value [env {:keys [name from to]}]
                      (edgex-get-device-readings name from to)))

(defquery-root :q/edgex-profile-yaml
               (value [env params]
                      (edgex-get-profile-yaml (:id params))))

(defquery-root :q/edgex-endpoints
  (value [env params]
         (e/edgex-get-endpoints)))

(defquery-entity :show-devices
  (value [{:keys [] :as env} id p]
         {:content (e/edgex-get :metadata "device" e/convert-device)
          :services (e/edgex-get :metadata "deviceservice" e/convert-device-service)
          :schedules (e/edgex-get :metadata "schedule" e/convert-schedule)
          :addressables (e/edgex-get :metadata "addressable" e/convert-addressable)
          :profiles (e/edgex-get :metadata "deviceprofile" e/convert-device-profile)}))

(defquery-entity :reading-page
  (value [{:keys [] :as env} id p]
         {:devices (e/edgex-get :metadata "device" e/convert-device)}))

(defquery-entity :show-commands
  (value [{:keys [] :as env} id p]
         (let [id (:id p)]
           {:source-device id
            :commands (edgex-get-device-commands id)})))

(defquery-entity :show-readings
  (value [env id {:keys [name from to]}]
         (let [readings (edgex-get-device-readings name from to)]
           {:device-name name
            :content readings})))

(defquery-entity :show-schedules
  (value [env id p]
         {:content (e/edgex-get :metadata "schedule" e/convert-schedule)
          :events (e/edgex-get :metadata "scheduleevent" e/convert-schedule-event)}))

(defquery-entity :show-profiles
  (value [env id p]
         {:content (e/edgex-get :metadata "deviceprofile" e/convert-device-profile)}))

(defquery-entity :show-addressables
  (value [env id p]
         {:content (e/edgex-get :metadata "addressable" e/convert-addressable)}))

(defn get-logs-in-time-range [ids coll start end limit]
  (let [logs (e/edgex-get :logging (str "logs/" start "/" end "/100") e/convert-log-entry)
        new-logs (filterv #(-> % :id ids not) logs)
        new-ids (set/union ids (into #{} (map :id new-logs)))
        new-coll (concat coll new-logs)]
    (if (or (< (count logs) 100) (<= limit 0))
      new-coll
      (recur new-ids new-coll (-> logs peek :created) end (dec limit)))))

(defquery-entity :show-logs
  (value [env id {:keys [start end]}]
         (let [logs (get-logs-in-time-range #{} [] start end 100)]
           {:content (into [] logs)})))

(defquery-entity :show-notifications
  (value [env id {:keys [start end]}]
         {:content (e/edgex-get :notifications (str "notification/" start "/" end "/100") e/convert-notification)}))

(defquery-entity :show-exports
  (value [env id p]
         {:content (e/edgex-get :export "registration" e/convert-export)}))

(defquery-entity :endpoint
  (value [env id p]
         (e/edgex-get-endpoints)))

