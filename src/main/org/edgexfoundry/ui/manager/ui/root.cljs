;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.root
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.dom :as dom]
            [fulcro.ui.forms :as f :refer [form-field* defvalidator]]
            [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.routing :refer [defrouter]]
            [fulcro.i18n :refer [tr trf]]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro-css.css :as css]
            [cognitect.transit :as t]
            [goog.object :as gobj]
            [org.edgexfoundry.ui.manager.ui.main :as main]
            [org.edgexfoundry.ui.manager.ui.routing :as r]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.devices :as dv]
            [org.edgexfoundry.ui.manager.ui.commands :as cmd]
            [org.edgexfoundry.ui.manager.ui.schedules :as sc]
            [org.edgexfoundry.ui.manager.ui.profiles :as p]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.logging :as logging]
            [org.edgexfoundry.ui.manager.ui.notifications :as nt]
            [org.edgexfoundry.ui.manager.ui.subscriptions :as sb]
            [org.edgexfoundry.ui.manager.ui.transmissions :as trn]
            [org.edgexfoundry.ui.manager.ui.addressables :as a]
            [org.edgexfoundry.ui.manager.ui.exports :as ex]
            [org.edgexfoundry.ui.manager.ui.readings :as rd]
            [org.edgexfoundry.ui.manager.ui.endpoints :as ep]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]))

(defn show-devices [this]
  (df/load this co/device-list-ident dv/DeviceList {:fallback `d/show-error})
  (df/load this co/profile-list-ident p/ProfileList {:fallback `d/show-error})
  (r/nav-to! this :main))

(defn show-readings [this]
  (df/load this co/reading-page-ident rd/ReadingsPage {:fallback `d/show-error})
  (r/nav-to! this :reading))

(defn show-schedules [this]
  (df/load this co/schedules-list-ident sc/ScheduleList {:fallback `d/show-error})
  (r/nav-to! this :schedule))

(defn show-profiles [this]
  (df/load this co/profile-list-ident p/ProfileList {:fallback `d/show-error})
  (r/nav-to! this :profile))

(defn show-addressables [this]
  (df/load this co/addressable-list-ident a/AddressableList {:fallback `d/show-error})
  (r/nav-to! this :addressable))

(defn show-notifications [this]
  (prim/transact! this `[(nt/load-notifications {})])
  (r/nav-to! this :notification))

(defn show-subscriptions [this]
  (ld/load this co/subscriptions-list-ident sb/SubscriptionList)
  (r/nav-to! this :subscription))

(defn show-transmissions [this]
  (prim/transact! this `[(trn/load-transmissions {})])
  (r/nav-to! this :transmission))

(defn show-exports [this]
  (df/load this co/exports-list-ident ex/ExportList {:fallback `d/show-error})
  (r/nav-to! this :export))

(defn show-logs [this]
  (prim/transact! this `[(logging/load-logs {})])
  (r/nav-to! this :logs))

(defn show-endpoint-modal [comp]
  (prim/transact! comp `[;(prepare-add-export {})
                         (b/show-modal {:id :endpoint-modal})
                         (m/toggle {:field :ui/menu-open?})]))

(defn show-download-history [this]
  (let [history (-> this prim/get-reconciler (prim/get-history) deref)
        w (t/writer :json)
        elem (.call (gobj/get js/document "createElement") js/document "a")
        body (gobj/get js/document "body")
        history (t/write w history)
        blob (js/Blob. #js [history] #js {:type "data:text/plain;charset=utf-8"})
        href (js/window.webkitURL.createObjectURL blob)]
    (prim/transact! this `[(m/toggle {:field :ui/menu-open?})])
    (.call (gobj/get elem "setAttribute") elem "href" href)
    (.call (gobj/get elem "setAttribute") elem "download" "support-data.dat")
    (-> elem
        (gobj/get "style")
        (gobj/get "display")
        (gobj/set "none"))
    (.call (gobj/get body "appendChild") body elem)
    (.call (gobj/get elem "click") elem)
    (.call (gobj/get body "removeChild") body elem)))

(defn menu-entry [label icon active fn]
  (let [attr (cond-> {:onClick fn}
               active (merge {:className "active"}))]
    (dom/li attr
            (dom/a nil
                   (dom/i #js {:className icon})
                   (dom/p #js {} label)))))

(defn sub-menu-entry [mini-label label sidebar? active? fn]
  (let [attr (cond-> {:onClick fn}
               active? (merge {:className "active"}))]
    (dom/li attr
      (dom/a nil
             (when sidebar?
               (dom/span #js {:className "sidebar-mini"} mini-label))
             (dom/span #js {:className "sidebar-normal"} label)))))

(defn relocatable-menu-items [this]
  [(dom/li {:onClick #(show-endpoint-modal this)
            :key :settings}
           (dom/a nil
                  (dom/i #js {:className "pe-7s-tools"})
                  " Settings "))
   (dom/li {:onClick #(show-download-history this)
            :key :support-history}
           (dom/a nil
                  (dom/i #js {:className "pe-7s-ticket"})
                  " Support Data "))])

(defn sidebar [this route-handler sidebar? nav-open? collapse toggle]
  (dom/div {:className "sidebar"}
    (dom/div #js {:className "logo"}
             (dom/a {:className "simple-text logo-mini"} " ")
             (dom/a {:className "logo-normal"}
                    (dom/img {:title "EdgeX" :src "img/logo.png" :style {:width "160px" :height "66px"}})))
    (dom/div #js {:className "sidebar-wrapper"}
             (when nav-open?
               (dom/ul {:className "nav"}
                       (relocatable-menu-items this)))
             (dom/ul
               #js {:className "nav"}
               (menu-entry "Devices" "pe-7s-wallet" (or (= route-handler :main) (= route-handler :info)) #(show-devices this))
               (menu-entry "Readings" "pe-7s-graph1" (= route-handler :reading) #(show-readings this))
               ;(menu-entry "Notifications" "pe-7s-bell" (= route-handler :notification) #(show-notifications this))
               (dom/li
                 #js {}
                 (dom/a #js {:onClick (fn [] (prim/transact! this `[(b/toggle-collapse {:id 1})
                                                                    (m/toggle {:field :ui/collapse-1-toggle})]))
                             :data-toggle "collapse"
                             :aria-expanded toggle}
                        (dom/i #js {:className "pe-7s-bell"})
                        (dom/p #js {}
                               "Notifications"
                               (dom/b #js {:className "caret"})))
                 (b/ui-collapse collapse
                                (dom/ul
                                  #js {:className "nav"}
                                  (sub-menu-entry "Nt" "Notifications" sidebar? (= route-handler :notification) #(show-notifications this))
                                  (sub-menu-entry "Sb" "Subscriptions" sidebar? (= route-handler :subscription) #(show-subscriptions this))
                                  (sub-menu-entry "Tr" "Transmissions" sidebar? (= route-handler :transmission) #(show-transmissions this)))))
               (menu-entry "Schedules" "pe-7s-clock" (or (= route-handler :schedule) (= route-handler :schedule-event)) #(show-schedules this))
               (menu-entry "Export" "pe-7s-next-2" (= route-handler :export) #(show-exports this))
               (menu-entry "Logs" "pe-7s-note2" (= route-handler :logs) #(show-logs this))
               ;(menu-entry "Rules Engine" "pe-7s-settings" false identity)
               (dom/li
                 #js {}
                 (dom/a #js {:onClick (fn [] (prim/transact! this `[(b/toggle-collapse {:id 1})
                                                                    (m/toggle {:field :ui/collapse-1-toggle})]))
                             :data-toggle "collapse"
                             :aria-expanded toggle}
                        (dom/i #js {:className "pe-7s-plugin"})
                        (dom/p #js {}
                               "Metadata"
                               (dom/b #js {:className "caret"})))
                 (b/ui-collapse collapse
                   (dom/ul
                     #js {:className "nav"}
                     (sub-menu-entry "Pr" "Profiles" sidebar? (= route-handler :profile) #(show-profiles this))
                     (sub-menu-entry "Ad" "Addressables" sidebar? (= route-handler :addressable) #(show-addressables this)))))))
    (dom/div #js {:className "sidebar-background" :style #js {:backgroundImage "url(img/side-bar.jpg)"}})))

(defn navbar [this sidebar? nav-open? menu-open?]
  (dom/nav
    #js {:className "navbar navbar-default"}
    (dom/div
      #js {:className "container-fluid"}
      (when sidebar?
        (dom/div
         #js {:className "navbar-minimize"}
         (dom/button
          #js
          {:id "minimizeSidebar",
           :className "btn btn-warning btn-fill btn-round btn-icon"
           :onClick #(m/toggle! this :ui/sidebar-open?)}
          (dom/i #js
                 {:className
                  "fa fa-ellipsis-v visible-on-sidebar-regular"})
          (dom/i #js
                 {:className
                  "fa fa-navicon visible-on-sidebar-mini"}))))
      (dom/div #js {:className "navbar-header"}
               (dom/button
                #js {:onClick #(m/toggle! this :ui/nav-open?)
                     :type "button",
                     :data-toggle "collapse",
                     :className (cond-> "navbar-toggle"
                                  nav-open? (str " toggled"))}
                (dom/span #js {:className "sr-only"}
                          "Toggle navigation")
                (dom/span #js {:className "icon-bar"})
                (dom/span #js {:className "icon-bar"})
                (dom/span #js {:className "icon-bar"}))
               (dom/a #js {:className "navbar-brand"}
                      "EdgeX Manager"))
      (dom/div
        #js {:className "collapse navbar-collapse"}
       (dom/ul
        #js {:className "nav navbar-nav navbar-right"}
        (dom/li
         #js {:className (cond-> "dropdown dropdown-with-icons"
                           menu-open? (str " open"))}
         (dom/a #js
                {:onClick #(m/toggle! this :ui/menu-open?)
                 :data-toggle "dropdown",
                 :className "dropdown-toggle"
                 :aria-haspopup true
                 :aria-expanded menu-open?}
                (dom/i #js {:className "fa fa-list" :id "dropdown-menu"})
                (dom/p #js {:className "hidden-md hidden-lg"}
                       " More "
                       (dom/b #js {:className "caret"})))
         (dom/ul
          #js {:className "dropdown-menu dropdown-with-icons"}
          (relocatable-menu-items this))))))))

(defn footer []
  (dom/footer
    #js {:className "footer"}
    (dom/div
      #js {:className "container-fluid"}
      (dom/nav #js {:className "pull-left"}
               (dom/ul #js {}
                       (dom/li #js {}
                               (dom/a {:href "http://www.edgexfoundry.org"} " EdgeX Foundry Website "))))
       "")))

(defsc Main [this {:keys [device-page ui/collapse-1 ui/collapse-1-toggle ui/sidebar-open? ui/menu-open? ui/nav-open?
                          ui/loading-data ui/route-handler]}]
  {:initial-state (fn [p] {:device-page (prim/get-initial-state main/MainPage nil)
                           :ui/collapse-1 (prim/get-initial-state b/Collapse {:id 1 :start-open false})
                           :ui/collapse-1-toggle false
                           :ui/sidebar-open? true
                           :ui/menu-open? false
                           :ui/nav-open? false
                           :ui/route-handler :main})
   :ident         (fn [] co/main-page-ident)
   :query         [{:device-page (prim/get-query main/MainPage)}
                   {:ui/collapse-1 (prim/get-query b/Collapse)}
                   :ui/collapse-1-toggle :ui/sidebar-open? :ui/menu-open? :ui/nav-open? :ui/route-handler [:ui/loading-data '_]]}
  (let [attr (if sidebar-open?
               nil
               #js {:className "sidebar-mini"})
        sidebar? false]
    (dom/div attr
             (dom/div #js {:className (cond-> "wrapper"
                                        nav-open? (str " nav-open"))}
                      (sidebar this route-handler sidebar? nav-open? collapse-1 collapse-1-toggle)
                      (dom/div
                        #js {:className "main-panel"}
                        (navbar this sidebar? nav-open? menu-open?)
                        (dom/div #js {:className "main-content"}
                                 (dom/div
                                   #js {:className "container-fluid"}
                                   (if loading-data
                                     (dom/div #js {:style #js {:padding "50%" :margin "-8px" :width "16px" :height "16px"}}
                                              (dom/i #js {:className "fa fa-cog fa-spin fa-3x fa-fw"}))
                                     (main/ui-main-page device-page))))
                        (footer))))))

(def ui-main (prim/factory Main))

(defsc ErrorModal [this {:keys [fulcro/server-error modal]}]
       {:initial-state (fn [p] {:modal (prim/get-initial-state b/Modal {:id :error-modal :backdrop true})})
        :ident (fn [] [:error-modal :singleton])
        :query [[:fulcro/server-error '_] {:modal (prim/get-query b/Modal)}]}
  (let [hide-modal (fn [] (prim/transact! this `[(b/hide-modal {:id :error-modal})]))]
       (b/ui-modal modal
                   (b/ui-modal-title nil
                                     (dom/div #js {:key "title"
                                                   :style #js {:fontSize "22px"}} "Error"))
                   (b/ui-modal-body nil
                                    (dom/div #js {:className "swal2-icon swal2-warning" :style #js {:display "block"}} "!")
                                    (dom/p #js {:key "message" :className b/text-danger} (:message server-error)))
                   (b/ui-modal-footer nil
                                      (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                                 :onClick #(hide-modal)}
                                                "OK")))))

(def ui-error-modal (prim/factory ErrorModal))

(defsc LocaleSelector [this {:keys [ui/locale available-locales]}]
  {:initial-state (fn [p] {:available-locales {"en" "English" "es" "Spanish"}})
   :ident         (fn [] [:ui-components/by-id :locale-selector])
   ; the weird-looking query here pulls data from the root node (where the current locale is stored) with a "link" query
   :query         [[:ui/locale '_] :available-locales]}
  (dom/div nil "Locale:" (map-indexed (fn [index [k v]]
                                        (dom/a #js {:key     index
                                                    :style   #js {:paddingRight "5px"}
                                                    :onClick #(prim/transact! this
                                                                              `[(m/change-locale {:lang ~k})])} v))
                                      available-locales)))

(def ui-locale (prim/factory LocaleSelector))

(defrouter ModalRouter :root/modal-router
  (fn [t p]
    (let [modal-id (:modal-id p)]
      (if modal-id
        [:delete-modal modal-id]
        [(or (:modal/page p) :new-addressable) :singleton])))
  :new-addressable a/AddAddressableModal
  :edit-addressable a/EditAddressableModal
  :admin-status-modal dv/AdminStatusModal
  :new-device dv/NewDeviceModal
  :set-command-modal cmd/SetCommandModal
  :new-notification nt/NotificationModal
  :new-subscription sb/SubscriptionModal
  :new-export ex/ExportModal
  :add-profile-modal p/AddProfileModal
  :new-schedule sc/AddScheduleModal
  :new-schedule-event sc/AddScheduleEventModal
  :delete-modal d/DeleteModal)

(def ui-modal-router (prim/factory ModalRouter))

(defsc Root [this {:keys [ui/react-key root/main ui/locale-selector ui/error-modal ui/modals ui/endpoint-modal]
                   :or {react-key "ROOT"}}]
  {:css [[:$modal-body {:max-height "calc(100vh - 210px)"
                        :overflow-y "auto"}]]
   :initial-state (fn [p] (merge
                           {:root/main       (prim/get-initial-state Main nil)
                            :ui/locale-selector (prim/get-initial-state LocaleSelector {})
                            :ui/error-modal (prim/get-initial-state ErrorModal {})
                            :ui/modals (prim/get-initial-state ModalRouter {})
                            :ui/endpoint-modal (prim/get-initial-state ep/EndpointModal {})}
                           r/app-routing-tree))
   :query         [:ui/locale :ui/react-key
                   {:root/main (prim/get-query Main)}
                   {:ui/locale-selector (prim/get-query LocaleSelector)}
                   {:ui/error-modal (prim/get-query ErrorModal)}
                   {:ui/modals (prim/get-query ModalRouter)}
                   {:ui/endpoint-modal (prim/get-query ep/EndpointModal)}
                   fulcro.client.routing/routing-tree-key]}
  (let [delete-cbs {:da-modal a/do-delete-addressable
                    :dd-modal dv/do-delete-device
                    :de-modal ex/do-delete-export
                    :dsb-modal sb/do-delete-subscription
                    :dp-modal p/do-delete-profile
                    :ds-modal sc/do-delete-schedule
                    :dse-modal sc/do-delete-schedule-event}]
    (dom/div #js {:key react-key}
             (ui-main main)
             (ui-error-modal error-modal)
             (ui-modal-router (prim/computed modals delete-cbs))
             (ep/ui-endpoint-modal endpoint-modal))))

(css/upsert-css "console-css" Root)
