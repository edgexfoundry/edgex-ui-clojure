;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.root
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
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
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.login :as lg]
            [org.edgexfoundry.ui.manager.ui.load :as ld]))

(defn show-devices [this]
  (df/load this co/device-list-ident dv/DeviceList {:fallback `d/show-error})
  (df/load this co/profile-list-ident p/ProfileList {:fallback `d/show-error})
  (r/nav-to! this :main))

(defn show-logout-modal [comp]
  (prim/transact! comp `[(b/show-modal {:id :logout-modal})
                         (m/toggle {:field :ui/menu-open?})]))

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

(defn clear-error* [state]
  (-> state
      (dissoc :fulcro/server-error)))

(defmutation clear-error [noargs]
  (action [{:keys [state]}]
          (swap! state clear-error*)))

(defn remove-error-popup [this]
  (prim/transact! this `[(clear-error)]))

(defsc MenuEntry [this {:keys [menu-entry/label menu-entry/icon menu-entry/active menu-entry/click-fn menu-entry/sub-menu]}]
  {:query [:db/id :menu-entry/label :menu-entry/icon :menu-entry/active :menu-entry/click-fn :menu-entry/sub-menu]
   :ident [:menu-entry/by-id :db/id]}
  (let [attr {:onClick #(click-fn this)
              :className (cond-> nil
                                 sub-menu (str "sub-menu")
                                 active (str " active"))}]
    (dom/li attr
            (dom/a nil
                   (dom/i {:className icon})
                   (dom/p {} label)))))

(def ui-menu-entry (prim/factory MenuEntry {:keyfn :db/id}))

(defn sub-menu-entry [mini-label label sidebar? active? fn]
  (let [attr (cond-> {:onClick fn}
               active? (merge {:className "active"}))]
    (dom/li attr
      (dom/a nil
             (when sidebar?
               (dom/span #js {:className "sidebar-mini"} mini-label))
             (dom/span #js {:className "sidebar-normal"} label)))))

(defsc RelocatableMenuItems [this props]
  (dom/ul {:className "dropdown-menu dropdown-with-icons"}
          ; Remove the Settings menu as the endpoint can be set on the Gateway menu
          (dom/li {:onClick #(show-endpoint-modal this)
                     :key     :settings}
                    (dom/a nil
                           (dom/i {:className "pe-7s-tools"})
                           " Settings "))
          (dom/li {:onClick #(show-download-history this)

                   :key :support-history}
                  (dom/a nil
                         (dom/i {:className "pe-7s-ticket"})
                         " Support Data "))
          (dom/li {:onClick #(show-logout-modal this)
                   :key :logout}
                  (dom/a nil
                         (dom/i :$pe-7s-power)
                         " Log out "))))

(def ui-relocatable-menu-items (prim/factory RelocatableMenuItems))

(defsc CollapseMenu [this {:keys [db/id collapse-menu/collapse collapse-menu/collapse-toggle collapse-menu/menu-id]} {:keys [activeMenu]}]
  {:query         [:db/id :collapse-menu/collapse-toggle :collapse-menu/menu-id
                   {:collapse-menu/collapse (prim/get-query b/Collapse)}]
   :initial-state (fn [{:keys [id menu-id]}]
                    {:db/id                         id
                     :collapse-menu/collapse-toggle false
                     :collapse-menu/collapse        (prim/get-initial-state b/Collapse {:id menu-id :start-open false})
                     :collapse-menu/menu-id         menu-id})
   :ident         [:collapse-menu/by-id :db/id]}
  (let [menu-label (if (= id :notification) "Notifications" "Metadata")
        menu-class (if (= id :notification) "pe-7s-bell" "pe-7s-plugin")
        notify-list [{:db/id :notification :menu-entry/label "Notifications" :menu-entry/icon "pe-7s-alarm" :menu-entry/active (= activeMenu :notification) :menu-entry/click-fn show-notifications :menu-entry/sub-menu true}
                     {:db/id :subscription :menu-entry/label "Subscriptions" :menu-entry/icon "pe-7s-mail" :menu-entry/active (= activeMenu :subscription) :menu-entry/click-fn show-subscriptions :menu-entry/sub-menu true}
                     {:db/id :transmission :menu-entry/label "Transmissions" :menu-entry/icon "pe-7s-graph2" :menu-entry/active (= activeMenu :transmission) :menu-entry/click-fn show-transmissions :menu-entry/sub-menu true}]
        metadata-list [{:db/id :profile :menu-entry/label "Profiles" :menu-entry/icon "pe-7s-file" :menu-entry/active (= activeMenu :profile) :menu-entry/click-fn show-profiles :menu-entry/sub-menu true}
                       {:db/id :addressable :menu-entry/label "Addressables" :menu-entry/icon "pe-7s-map-marker" :menu-entry/active (= activeMenu :addressable) :menu-entry/click-fn show-addressables :menu-entry/sub-menu true}]]
    (dom/li nil
            (dom/a {:onClick (fn [] (prim/transact! this `[(b/toggle-collapse {:id ~menu-id})
                                                           (m/toggle {:field :collapse-menu/collapse-toggle})]))
                    :data-toggle   "collapse"
                    :aria-expanded collapse-toggle}
                   (dom/i {:className menu-class})
                   (dom/p {}
                          menu-label
                          (dom/b {:className "caret"})))
            (b/ui-collapse collapse
                           (dom/ul {:className "nav"}
                                   (if (= id :notification)
                                     (map ui-menu-entry notify-list)
                                     (map ui-menu-entry metadata-list)))))))

(def ui-collapse-menu (prim/factory CollapseMenu {}))

(defsc SideBar [this {:keys [side-bar/collapse-menu side-bar/user]} {:keys [activeMenu]}]
  {:initial-state (fn [p] {:side-bar/user ""
                           :side-bar/collapse-menu [(prim/get-initial-state CollapseMenu {:id :notification :menu-id 1})
                                                    (prim/get-initial-state CollapseMenu {:id :metadata :menu-id 2})]})
   :query [:side-bar/user
           {:side-bar/collapse-menu (prim/get-query CollapseMenu)}]
   :ident (fn [] [::side-bar :singleton])}
  (dom/div :$sidebar
           (dom/div :$logo
                    (dom/a :$simple-text$logo-mini " ")
                    (dom/a :$logo-normal
                           (dom/img {:title "IOTech" :src "img/logo.png" :style {:width "160px" :height "66px"}})))
           (dom/div :$sidebar-wrapper
                    (dom/ul :$nav
                            (ui-menu-entry {:db/id :device :menu-entry/label "Devices" :menu-entry/icon "pe-7s-wallet" :menu-entry/active (= activeMenu :device) :menu-entry/click-fn show-devices})
                            (ui-menu-entry {:db/id :reading :menu-entry/label "Readings" :menu-entry/icon "pe-7s-graph1" :menu-entry/active (= activeMenu :reading) :menu-entry/click-fn show-readings})
                            (ui-menu-entry {:db/id :schedule :menu-entry/label "Schedules" :menu-entry/icon "pe-7s-clock" :menu-entry/active (= activeMenu :schedule) :menu-entry/click-fn show-schedules})
                            (ui-menu-entry {:db/id :export :menu-entry/label "Export" :menu-entry/icon "pe-7s-next-2" :menu-entry/active (= activeMenu :export) :menu-entry/click-fn show-exports})
                            (ui-menu-entry {:db/id :log :menu-entry/label "Logs" :menu-entry/icon "pe-7s-note2" :menu-entry/active (= activeMenu :log) :menu-entry/click-fn show-logs})
                            (map (fn [p] (ui-collapse-menu (prim/computed p {:activeMenu activeMenu}))) collapse-menu)))
           #_(dom/div :$sidebar-background {:style {:backgroundImage "url(img/side-bar.jpg)"}})))

(def ui-side-bar (prim/factory SideBar))

(defsc NavBar [this {:keys [nav-bar/nav-open? nav-bar/menu-open?]} {:keys [toggle-side-bar]}]
  {:initial-state (fn [p] {:nav-bar/nav-open? true :nav-bar/menu-open? false })
   :query         [:nav-bar/nav-open? :nav-bar/menu-open? ]
   :ident         (fn [] [::nav-bar :singleton])}
  (dom/nav {:className "navbar navbar-default"}
           (dom/div {:className "container-fluid"}
                    (dom/div {:className "navbar-minimize"}
                             (dom/button
                               {:id        "minimizeSidebar",
                                :className "btn btn-warning btn-fill btn-round btn-icon"
                                :onClick #(toggle-side-bar)}
                               (dom/i {:className "fa fa-ellipsis-v visible-on-sidebar-regular"})
                               (dom/i {:className "fa fa-navicon visible-on-sidebar-mini"})))
                    (dom/div {:className "navbar-header"}
                             (dom/button
                               {:onClick #(m/toggle! this :nav-bar/nav-open?)
                                :type "button",
                                :data-toggle "collapse",
                                :className (cond-> "navbar-toggle"
                                                   nav-open? (str " toggled"))}
                               (dom/span {:className "sr-only"}
                                         "Toggle navigation")
                               (dom/span {:className "icon-bar"})
                               (dom/span {:className "icon-bar"})
                               (dom/span {:className "icon-bar"}))
                             (dom/a {:className "navbar-brand"}
                                    "Edge Xpert Manager"))
                    (dom/div {:className "collapse navbar-collapse"}
                             (dom/ul {:className "nav navbar-nav navbar-right"}
                                     (dom/li {:className (cond-> "dropdown dropdown-with-icons"
                                                                 menu-open? (str " open"))}
                                             (dom/a {:onClick #(m/toggle! this :nav-bar/menu-open?)
                                                     :data-toggle "dropdown",
                                                     :className "dropdown-toggle"
                                                     :aria-haspopup true
                                                     :aria-expanded menu-open?
                                                     }
                                                    (dom/i {:className "fa fa-list" :id "dropdown-menu"})
                                                    (dom/p {:className "hidden-md hidden-lg"}
                                                           " More "
                                                           (dom/b {:className "caret"})))
                                             (ui-relocatable-menu-items {})))))))

(def ui-nav-bar (prim/factory NavBar))

(defn footer []
  (dom/footer {:className "footer"}
    (dom/div {:className "container-fluid"}
      (dom/nav {:className "pull-left"}
               (dom/ul
                       (dom/li {}
                               (dom/a {:href "http://www.edgexfoundry.org"} " EdgeX Foundry Website "))))
       "")))

(defsc ErrorPop [this {:keys [fulcro/server-error]} ]
  {:initial-state (fn [params] {:id :error-pop})
   :query         [:id [:fulcro/server-error '_]]
   :ident         (fn [] [::error-pop :singleton])}
  (let [error-message (:message server-error)]
    (when error-message
      (dom/div {:style {:display "inline-block"
                        :margin "0px auto"
                        :position "fixed"
                        :zIndex "1031"
                        :top "20px"
                        :right "20px"}
                :className "col-xs-11 col-sm-4 alert alert-danger alert-with-icon"}
               (dom/button {:type "button"
                            :onClick #(remove-error-popup this)
                            :aria-hidden "true"
                            :style {:position "absolute"
                                    :right "10px"
                                    :top "50%"
                                    :marginTop "-13px"
                                    :zIndex "1033"}
                            :className "close"} "×")
               (dom/span {:data-notify "icon"
                          :className "pe-7s-attention"})
               (dom/span {:data-notify "title"})
               (dom/span {:data-notify "message"} error-message)))))

(def ui-error-pop (prim/factory ErrorPop))

(defsc Main [this {:keys [device-data ui/sidebar-open? ui/nav-open? page ui/loading-data sidebar navbar error-pop] :as props}]
  {:initial-state (fn [p] {:ui/sidebar-open? true
                           :ui/nav-open?     false
                           :page             :device :device-data (prim/get-initial-state main/DeviceListOrInfoRouter {})
                           :sidebar          (prim/get-initial-state SideBar {})
                           :navbar           (prim/get-initial-state NavBar {})
                           :error-pop        (prim/get-initial-state ErrorPop {})})
   :ident         (fn [] co/main-page-ident)
   :query         [:page {:device-data (prim/get-query main/DeviceListOrInfoRouter)}
                   :ui/sidebar-open? :ui/nav-open?
                   [df/marker-table :readings-marker]
                   [:ui/loading-data '_]
                   {:sidebar (prim/get-query SideBar)}
                   {:navbar (prim/get-query NavBar)}
                   {:error-pop (prim/get-query ErrorPop)}]}
  (let [attr (if sidebar-open?
               nil
               {:className "sidebar-mini"})
        no-graph-load (-> props (get [df/marker-table :readings-marker]) not)
        user (.getItem (.-localStorage js/window) :xpert-mgr-user)
        toggle-side-bar (fn [] (m/set-value! this :ui/sidebar-open? (not sidebar-open?)))
        active-menu (main/select-active-menu (:fulcro.client.routing/current-route device-data))]
    (dom/div nil
             (dom/div attr
                      (dom/div {:className (cond-> "wrapper"
                                                   nav-open? (str " nav-open"))}
                               (ui-side-bar (prim/computed sidebar {:activeMenu active-menu}))
                               (dom/div {:className "main-panel"}
                                        (ui-nav-bar (prim/computed navbar {:toggle-side-bar toggle-side-bar}))
                                        (dom/div {:className "main-content"}
                                                 (dom/div :$container-fluid
                                                          (if (and loading-data no-graph-load)
                                                            (dom/div {:style {:padding "50%" :margin "-8px" :width "16px" :height "16px"}}
                                                                     (dom/i {:className "fa fa-cog fa-spin fa-3x fa-fw"}))
                                                            (if-not (nil? page)
                                                              (dom/div nil
                                                                       (main/ui-device-list-or-info device-data))))))
                                        (footer))))
             (ui-error-pop error-pop))))

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
  :change-pw lg/ChangePWModal
  :delete-modal d/DeleteModal)

(def ui-modal-router (prim/factory ModalRouter))

(defn select-router [props]
  (condp #(contains? %2 %1) props
    :ui/password [:login :top]
    :pw-updated? [:login :top]
    :device-data [:device :top]
    [:device :top]))

(defrouter TopRouter :top-router
           (fn [this props]
             (if (contains? props :page)
               [(:page props) :top]
               (select-router props)))
           :login lg/LoginPage
           :device Main)

(def ui-top (prim/factory TopRouter))

(defsc Root [this {:keys [ui/react-key ui/locale-selector ui/error-modal ui/modals ui/endpoint-modal ui/logout-modal top-router]
                   :or {react-key "ROOT"}}]
  {:css [[:$modal-body {:max-height "calc(100vh - 210px)"
                        :overflow-y "auto"}]]
   :initial-state (fn [p] (merge
                           {:ui/locale-selector (prim/get-initial-state LocaleSelector {})
                            :ui/error-modal (prim/get-initial-state ErrorModal {})
                            :ui/modals (prim/get-initial-state ModalRouter {})
                            :ui/endpoint-modal (prim/get-initial-state ep/EndpointModal {})
                            :ui/logout-modal    (prim/get-initial-state lg/LogoutModal {})
                            :top-router         (prim/get-initial-state TopRouter {})
                            :pw-updated?         false}
                           r/app-routing-tree))
   :query         [:ui/locale :ui/react-key
                   {:root/main (prim/get-query Main)}
                   {:ui/locale-selector (prim/get-query LocaleSelector)}
                   {:ui/error-modal (prim/get-query ErrorModal)}
                   {:ui/modals (prim/get-query ModalRouter)}
                   {:ui/endpoint-modal (prim/get-query ep/EndpointModal)}
                   {:ui/logout-modal    (prim/get-query lg/LogoutModal)}
                   {:top-router         (prim/get-query TopRouter)}
                   :ui/loading-data
                   fulcro.client.routing/routing-tree-key
                   :pw-updated?]}
  (let [delete-cbs {:da-modal a/do-delete-addressable
                    :dd-modal dv/do-delete-device
                    :de-modal ex/do-delete-export
                    :dnt-modal nt/do-delete-notification
                    :dsb-modal sb/do-delete-subscription
                    :dp-modal p/do-delete-profile
                    :ds-modal sc/do-delete-schedule
                    :dse-modal sc/do-delete-schedule-event}]
    (dom/div #js {:key react-key}
             (ui-top top-router)
             ;(ui-error-modal error-modal)
             (ui-modal-router (prim/computed modals delete-cbs))
             (ep/ui-endpoint-modal endpoint-modal)
             (lg/ui-logout-modal logout-modal))))

(css/upsert-css "console-css" Root)
