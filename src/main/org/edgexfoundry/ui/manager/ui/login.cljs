;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.login
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.routing :as r]
            [fulcro.events :as evt]
            [fulcro.i18n :refer [tr]]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.form-state :as fs]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.load :as ld]))

(declare ChangePWModal)

(declare LoginPage)

(defn prepare-change-pw* [state]
  (let [ref co/change-pw-ident]
    (-> state
        (fs/add-form-config* ChangePWModal ref))))

(defmutation prepare-change-pw
             [args]
             (action [{:keys [state]}]
                     (swap! state (fn [s] (prepare-change-pw* s)))))

(defn show-change-pw-modal [comp]
  (prim/transact! comp `[(prepare-change-pw {})
                         (r/set-route {:router :root/modal-router :target ~co/change-pw-ident})
                         (b/show-modal {:id :change-pw-modal})
                         :modal-router]))

(defmutation change-pw-failed
  [args]
  (action [{:keys [state] :as env}]
          (swap! state assoc :pw-updated? false)))

(defmutation change-pw-complete
  [args]
  (action [{:keys [state] :as env}]
          (swap! state assoc :pw-updated? true  :fulcro/server-error nil)))

(defmutation change-password
  [{:keys [oldpassword newpassword]}]
  (action [{:keys [state] :as env}]
          (df/load-action env :q/change-pw LoginPage
                          {:post-mutation `change-pw-complete
                           :fallback `change-pw-failed
                           :params {:oldpw oldpassword :newpw newpassword}})))

(defsc LoginPage [this {:keys [ui/password fulcro/server-error pw-updated?]}]
  {:initial-state (fn [params] {:id :login :ui/password ""})
   :query         [:id :ui/password
                   [:pw-updated? '_]
                   [:fulcro/server-error '_]]
   :ident         (fn [] co/login-page-ident)}
  (let [bad-cred (:message server-error)
        login            (fn []
                           (df/load this :q/login LoginPage {:post-mutation `mu/login-complete
                                                             :params        {:password password}}))]
    (dom/div :$login-wrap
             (dom/div :$login-html
                      (dom/div :$login-form
                               (dom/div :$welcome "Welcome to EdgeX Manager")
                               (dom/div :$subtitle "Please enter your password to login")
                               (dom/div :$login-pw
                                        (b/labeled-input {:id "password" :value password :type "password" :split 3 :placeholder "Password"
                                                          :onKeyDown (fn [evt] (when (evt/enter-key? evt) (login))) :onChange #(m/set-string! this :ui/password :event %)} nil))
                               (b/button {:key     "login-button" :className "btn-fill" :kind :info
                                          :onClick login} "Login")
                               (dom/div :$foot-link
                                        (dom/a {:style {:cursor "pointer"} :onClick #(show-change-pw-modal this)} (tr "Change Password")))
                               (when (= bad-cred "Invalid Password")
                                 (dom/div :$err-msg
                                          (dom/i #js {:className "pe-7s-attention"})
                                          (tr "The current password you have entered is incorrect. Please try again to log in.")))
                               (when (= bad-cred "Invalid Current Password")
                                 (dom/div :$err-msg
                                          (dom/i #js {:className "pe-7s-attention"})
                                          (tr "The current password you have entered is incorrect. Failed to change password.")))
                               (when pw-updated?
                                 (dom/div :$success-msg
                                          (dom/i #js {:className "pe-7s-check"})
                                          (tr "Password updated successfully. Please use the new password to login."))))))))

(defsc LogoutModal [this {:keys [modal] :as props}]
  {:initial-state (fn [p] {:modal (prim/get-initial-state b/Modal {:id :logout-modal :backdrop true})})
   :ident (fn [] co/logout-ident)
   :query [{:modal (prim/get-query b/Modal)}]}
  (let [hide-modal (fn [modal-id] (prim/transact! this `[(b/hide-modal {:id ~modal-id})]))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div {:key "title" :style {:fontSize "22px"}} "Confirm Logout"))
                (b/ui-modal-body nil
                                 (dom/div {:className "swal2-icon swal2-warning" :style {:display "block"}} "!")
                                 (dom/p {:key "message" :className b/text-danger} (str "Are you sure to log out?")))
                (b/ui-modal-footer nil
                                   (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                              :onClick #(do
                                                          (prim/transact! this `[(mu/logout {})])
                                                          ;(prim/props this)
                                                          (hide-modal :logout-modal))}
                                             "OK")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(hide-modal :logout-modal)}
                                             "Cancel")))))

(def ui-logout-modal (prim/factory LogoutModal))

(defn new-user-form-valid [form field]
  (let [v (get form field)]
    (case field
      :ui/oldpassword (and (string? v) (seq (str/trim v))) ; not empty
      :ui/newpassword-2 (= v (:ui/newpassword form)))))          ; passwords match

(def validator (fs/make-validator new-user-form-valid))

(s/def :ui/oldpassword #(re-matches #"\S+" %))
(s/def :ui/newpassword #(re-matches #"\S+" %))
(s/def :ui/newpassword-2 #(re-matches #"\S+" %))

(defsc ChangePWModal [this {:keys [ui/oldpassword ui/newpassword modal] :as props}]
  {:initial-state (fn [p] {:ui/oldpassword "" :ui/newpassword "" :ui/newpassword-2 ""
                           :modal (prim/get-initial-state b/Modal {:id :change-pw-modal :backdrop true})
                           :modal/page :change-pw})
   :ident (fn [] co/change-pw-ident)
   :query [:ui/oldpassword :ui/newpassword :ui/newpassword-2 fs/form-config-join :id
           {:modal (prim/get-query b/Modal)} :modal/page]
   :form-fields #{:ui/oldpassword :ui/newpassword :ui/newpassword-2}}
  (let [cancel (fn [evt] (prim/transact! this `[(b/hide-modal {:id :change-pw-modal})
                                                (fs/reset-form!)
                                                (fs/clear-complete!)]))
        save (fn [evt] (prim/transact! this `[(change-password {:oldpassword ~oldpassword :newpassword ~newpassword})
                                              (b/hide-modal {:id :change-pw-modal})
                                              (fs/reset-form!)
                                              (fs/clear-complete!)
                                              (df/fallback {:action ld/reset-error})]))
        validity (validator props :ui/newpassword-2)
        is-invalid? (= :invalid validity)
        disable-button (or (not (fs/checked? props))(fs/invalid-spec? props) is-invalid?)]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div {:key "title" :style {:fontSize "22px"}} (tr "Change Password")))
      (b/ui-modal-body nil
                       (dom/div :$card
                                (dom/div :$content
                                         (dom/div :$form-group$change-pw
                                                  (co/input-with-label this :ui/oldpassword "Current password:" "Current password is required" "" nil {:type "password"})
                                                  (co/input-with-label this :ui/newpassword "New password:" "New password is required" "" nil {:type "password"})
                                                  (co/input-with-label this :ui/newpassword-2 "Re-enter new password:" "Confirm password is required" "" nil {:type "password"})
                                                  (when (and (not (fs/invalid-spec? props :ui/newpassword-2)) is-invalid?)
                                                    (dom/span :$warning "New password doesn't match the confirm password."))))))
      (b/ui-modal-footer nil
                         (b/button {:key "save-button" :className "btn-fill" :kind :info :disabled disable-button :onClick save} "Save")
                         (b/button {:key "cancel-button" :className "btn-fill" :kind :danger :onClick cancel} "Cancel")))))

(def ui-change-pw-modal (prim/factory ChangePWModal))
