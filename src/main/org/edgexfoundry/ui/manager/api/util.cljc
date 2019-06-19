;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.util)

(defn mk-export [addressable format destination compression encryptionAlgorithm encryptionKey
                 initializingVector device-filter reading-filter enable]
  (let [addr (select-keys addressable [:address :method :name :origin :password :path :port :protocol
                                       :publisher :topic :user :cert :key])
                ex {:addressable addr
                    :format format
                    :destination destination
                    :compression compression
                    :encryption {:encryptionAlgorithm encryptionAlgorithm}
                    :filter {:deviceIdentifiers device-filter
                             :valueDescriptorIdentifiers reading-filter}
                    :enable enable}
                assoc-if-encrypt (fn [key val ex]
                                   (if (= encryptionAlgorithm :NONE)
                                     ex
                                     (assoc-in ex [:encryption key] val)))]
                (->> ex
                     (assoc-if-encrypt :encryptionKey encryptionKey)
                     (assoc-if-encrypt :initializingVector initializingVector))))
