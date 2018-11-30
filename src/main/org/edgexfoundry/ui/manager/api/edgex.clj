;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.edgex
  (:require [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [clj-http.client :as http]
            [taoensso.timbre :as timbre]))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn key-to-string [k]
  (subs (str k) 1))

(defn to-key [map ka] (reduce (fn [m k] (update-in m k keyword)) map ka))

(defn convert-basic [s type]
  (-> s
      walk/keywordize-keys
      (to-key [[:id]])
      (assoc :type type)))

(defn convert [d keys type]
  (-> d
      (convert-basic type)
      (select-keys keys)
      (to-key [[:id] [:adminState] [:operatingState] [:service :adminState] [:service :operatingState]])))

(defn convert-device [d]
  (-> d
      (convert [:id :description :adminState :operatingState :name :labels :service :profile :lastConnected :lastReported
                :profile :addressable] :device)
      (to-key [[:profile :id]])
      (dissoc-in [:profile :deviceResources])
      (dissoc-in [:profile :commands])
      (dissoc-in [:profile :resources])))

(defn convert-device-service [d]
  (-> d
      (convert [:id :adminState :operatingState :name :addressable :labels] :device-service)
      (to-key [[:addressable :id]])))

(defn convert-device-profile [p]
  (convert-basic p :device-profile))

(defn convert-addressable [a]
  (convert-basic a :addressable))

(defn convert-schedule [s]
  (let [add-default (fn [s k] (assoc s k (or (get s k) 0)))]
    (-> s
        (convert-basic :schedule)
        (add-default :start)
        (add-default :end))))

(defn convert-schedule-event [s]
  (-> s
      (convert-basic :schedule-event)
      (to-key [[:addressable :id]])))

(defn convert-resource [r]
  (convert-basic r :resource))

(defn convert-command [c]
  (convert-basic c :command))

(defn convert-value-descriptor [c]
  (-> (set/rename-keys c {"type" "value-type"})
      (convert-basic :value-descriptor)))

(defn convert-reading [e]
  (let [readings (-> e walk/keywordize-keys :readings)]
    (mapv #(convert-basic % :reading) readings)))

(defn convert-log-entry [e]
  (let [id (.toString (java.util.UUID/randomUUID))]
    (convert-basic (assoc e "id" id) :log-entry)))

(defn convert-notification [c]
  (convert-basic c :notification))

(defn convert-export [e]
  (-> (set/rename-keys e {"_id" "id"})
      (convert-basic :export)
      (to-key [[:destination] [:format] [:compression] [:encryption :encryptionAlgorithm]])))

(defonce endpoints (atom nil))

(defn set-endpoints! [e]
  (reset! endpoints e))

(defn edgex-get-path-raw [source path]
  (-> (str "http://" (get @endpoints source) "/api/v1/" path)
      http/get
      :body))

(defn edgex-get-path [source path]
  (json/read-str (edgex-get-path-raw source path)))

(defn edgex-get [source path convertor]
  (mapv convertor (edgex-get-path source path)))

(defn edgex-post [target path data]
  (http/post (str "http://" (get @endpoints target) "/api/v1/" path)
           {:form-params (walk/stringify-keys data)
            :content-type :json}))

(defn edgex-put [target path data]
  (http/put (str "http://" (get @endpoints target) "/api/v1/" path)
           {:body (json/write-str data)}))

(defn edgex-delete [target path]
  (http/delete (str "http://" (get @endpoints target ) "/api/v1/" path))
  nil)

(defn edgex-put-command [url values]
  (let [conv-val (fn [[name kind value]]
                   [name value])
        data (into {} (map conv-val values))]
    (timbre/info "edgex-put-command" url data)
    (http/put url {:body (json/write-str data)})
    nil))

(defn edgex-get-endpoints []
  (deref endpoints))
