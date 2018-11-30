;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.client
  (:require [fulcro.client.primitives :as om]
            [fulcro.client.data-fetch :as df]
            [fulcro.client :as fc]
            [fulcro.client.network :as net]
            [org.edgexfoundry.ui.manager.ui.root :as root]
            [fulcro.ui.file-upload :as upload]
            [fulcro.ui.forms :as f]
            [org.edgexfoundry.ui.manager.ui.devices :as devices]
            [org.edgexfoundry.ui.manager.ui.schedules :as sc]
            [org.edgexfoundry.ui.manager.ui.exports :as ex]
            [org.edgexfoundry.ui.manager.ui.endpoints :as ep]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.routing :as r]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]))

(defonce app (atom nil))

(defn mount []
  (reset! app (fc/mount @app root/Root "app")))

(defn start []
  (mount))

(defonce upload-networking (upload/file-upload-networking))

(defn- initialize-form [state-map form-class form-ident]
  (update-in state-map form-ident #(f/build-form form-class %)))

(defmutation build-form [noparams]
  (action [{:keys [state]}]
          (swap! state (fn [state-map]
                         (-> state-map
                             (initialize-form ep/EndpointForm co/endpoint-ident))))))

(defn ^:export init []
  (reset! app (fc/new-fulcro-client
                     :started-callback (fn [{:keys [reconciler] :as app}]
                                         (upload/install-reconciler! upload-networking reconciler)
                                         (df/load app :q/edgex-devices devices/DeviceListEntry
                                                 {:target (df/multiple-targets
                                                            (conj co/device-list-ident :content)
                                                            (conj co/reading-page-ident :devices))
                                                  :marker false
                                                  :fallback `d/show-error})
                                         (df/load app :q/edgex-device-services devices/ServiceListEntry
                                                 {:target (conj co/device-list-ident :services)
                                                  :fallback `d/show-error})
                                         (df/load app :q/edgex-schedule-events devices/ScheduleEventListEntry
                                                 {:target (conj co/device-list-ident :schedule-events)
                                                  :fallback `d/show-error})
                                         (df/load app :q/edgex-addressables devices/AddressableListEntry
                                                 {:target (df/multiple-targets
                                                            (conj co/device-list-ident :addressables)
                                                            (conj co/new-export-ident :addressables)
                                                            (conj co/edit-export-ident :addressables))
                                                  :marker false
                                                  :fallback `d/show-error})
                                         (df/load app :q/edgex-profiles devices/ProfileListEntry
                                                 {:target (df/multiple-targets
                                                            (conj co/profile-list-ident :content)
                                                            (conj co/new-device-ident :profiles))
                                                  :marker false
                                                  :fallback `d/show-error})
                                         (df/load app co/schedules-list-ident sc/ScheduleList {:fallback `d/show-error})
                                         (df/load app co/exports-list-ident ex/ExportList {:fallback `d/show-error})
                                         (df/load app co/endpoint-ident ep/EndpointForm {:post-mutation `build-form})
                                         (r/start-routing reconciler))
                     :networking {:remote      (net/make-fulcro-network "/api" :global-error-callback identity)
                                  :file-upload upload-networking}))
  (start))
