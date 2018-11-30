;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.util)

(defn mk-export [addressable format destination compression encryptionAlgorithm encryptionKey
                 initializingVector enable]
  (let [addr (select-keys addressable [:address :method :name :origin :password :path :port :protocol
                                       :publisher :topic :user])
                ex {:addressable addr
                    :format (case format
                              :JSON "JSON"
                              :XML "XML"
                              :IOTCORE_JSON "IOTCORE_JSON"
                              :AZURE_JSON "AZURE_JSON"
                              :THINGSBOARD_JSON "THINGSBOARD_JSON"
                              :NOOP "NOOP")
                    :destination (case destination
                                   :MQTT_TOPIC "MQTT_TOPIC"
                                   :ZMQ_TOPIC "ZMQ_TOPIC"
                                   :REST_ENDPOINT "REST_ENDPOINT"
                                   :IOTCORE_TOPIC "IOTCORE_TOPIC"
                                   :AZURE_TOPIC "AZURE_TOPIC"
                                   :XMPP_TOPIC "XMPP_TOPIC"
                                   :AWS_TOPIC "AWS_TOPIC"
                                   :INFLUXDB_ENDPOINT "INFLUXDB_ENDPOINT")
                    :compression (case compression
                                   :NONE "NONE"
                                   :GZIP "GZIP"
                                   :ZIP "ZIP")
                    :encryption {:encryptionAlgorithm (case encryptionAlgorithm
                                                        :NONE "NONE"
                                                        :AES "AES")}
                    :enable enable}
                assoc-if-encrypt (fn [key val ex]
                                   (if (= encryptionAlgorithm :NONE)
                                     ex
                                     (assoc-in ex [:encryption key] val)))]
                (->> ex
                     (assoc-if-encrypt :encryptionKey encryptionKey)
                     (assoc-if-encrypt :initializingVector initializingVector))))
