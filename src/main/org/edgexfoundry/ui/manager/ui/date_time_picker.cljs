;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.date-time-picker
  (:require [org.edgexfoundry.ui.manager.ui.common :as co]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :refer [defsc]]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.primitives :as prim]
            [fulcro.ui.bootstrap3 :as b]
            [cljs-time.coerce :as ct]
            [cljs-time.format :as ft]
            [cljs-time.core :as tc]
            [goog.object :as gobj]))

(def time-formatter (ft/formatter "dd/MM/y HH:mm"))

(defn get-cal [state id attr]
  (get-in state [:date-time-picker id attr]))

(defn get-cal-attr [state id attr f]
  (let [time (-> (get-cal state id :time) ct/from-long tc/to-default-time-zone)]
    (or (get-cal state id attr) (f time))))

(defn update-time [state id]
  (let [year (get-cal-attr state id :year tc/year)
        month (get-cal-attr state id :month tc/month)
        day (get-cal-attr state id :day tc/day)
        hour (get-cal-attr state id :hour tc/hour)
        minute (get-cal-attr state id :minute tc/minute)]
    (let [time (tc/from-default-time-zone (tc/date-time year month day hour minute))]
      (-> state
          (assoc-in [:date-time-picker id :time] (ct/to-long time))
          (assoc-in [:date-time-picker id :display] (->> time tc/to-default-time-zone (ft/unparse time-formatter)))
          (assoc-in [:date-time-picker id :no-default?] false)))))

(defn adj-date-time* [state id adjuster commit]
  (let [year (get-cal-attr state id :year tc/year)
        month (get-cal-attr state id :month tc/month)
        hour (get-cal-attr state id :hour tc/hour)
        minute (get-cal-attr state id :minute tc/minute)
        [new-year new-month new-hour new-minute] (adjuster year month hour minute)
        maybe-commit #(if commit (update-time % id) %)]
    (-> state
        (assoc-in [:date-time-picker id :month] new-month)
        (assoc-in [:date-time-picker id :year] new-year)
        (assoc-in [:date-time-picker id :hour] new-hour)
        (assoc-in [:date-time-picker id :minute] new-minute)
        (maybe-commit))))

(defn update-day* [state id date]
  (-> state
      (assoc-in [:date-time-picker id :day] (tc/day date))
      (assoc-in [:date-time-picker id :month] (tc/month date))
      (assoc-in [:date-time-picker id :year] (tc/year date))
      (update-time id)))

(defn update-month* [state id month]
  (-> state
      (assoc-in [:date-time-picker id :month] month)
      (assoc-in [:date-time-picker id :mode] :calendar)))

(defn update-hour* [state id hour]
  (let [current-hour (get-cal-attr state id :hour tc/hour)
        new-hour (if (> current-hour 12) (+ hour 12) hour)]
    (-> state
        (assoc-in [:date-time-picker id :hour] new-hour)
        (update-time id)
        (assoc-in [:date-time-picker id :mode] :time))))

(defn update-minute* [state id minute]
  (-> state
      (assoc-in [:date-time-picker id :minute] minute)
      (update-time id)
      (assoc-in [:date-time-picker id :mode] :time)))

(defn update-if-valid* [state id time-str]
  (let [new-time (try (ft/parse time-formatter time-str) (catch js/Error _ false))]
    (if (tc/date? new-time)
      (-> state
          (assoc-in [:date-time-picker id :time] (ct/to-long new-time))
          (assoc-in [:date-time-picker id :minute] (tc/minute new-time))
          (assoc-in [:date-time-picker id :hour] (tc/hour new-time))
          (assoc-in [:date-time-picker id :day] (tc/day new-time))
          (assoc-in [:date-time-picker id :month] (tc/month new-time))
          (assoc-in [:date-time-picker id :year] (tc/year new-time)))
      state)))

(defn dec-month [year month hour minute]
  (if (= month 1)
    [(dec year) 12 hour minute]
    [year (dec month) hour minute]))

(defn inc-month [year month hour minute]
  (if (= month 12)
    [(inc year) 1 hour minute]
    [year (inc month) hour minute]))

(defn dec-year [year month hour minute]
  [(dec year) month hour minute])

(defn inc-year [year month hour minute]
  [(inc year) month hour minute])

(defn dec-hour [year month hour minute]
  (if (= hour 0)
    [year month 23 minute]
    [year month (dec hour) minute]))

(defn inc-hour [year month hour minute]
  (if (= hour 23)
    [year month 0 minute]
    [year month (inc hour) minute]))

(defn dec-minute [year month hour minute]
  (if (= minute 0)
    [year month hour 59]
    [year month hour (dec minute)]))

(defn inc-minute [year month hour minute]
  (if (= minute 59)
    [year month hour 0]
    [year month hour (inc minute)]))

(defn toggle-am-pm* [year month hour minute]
  (if (> hour 12)
    [year month (- hour 12) minute]
    [year month (+ hour 12) minute]))

(defmutation previous-month
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id dec-month false)))))

(defmutation next-month
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id inc-month false)))))

(defmutation previous-year
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id dec-year false)))))

(defmutation next-year
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id inc-year false)))))

(defmutation previous-hour
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id dec-hour true)))))

(defmutation next-hour
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id inc-hour true)))))

(defmutation previous-minute
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id dec-minute true)))))

(defmutation next-minute
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id inc-minute true)))))

(defmutation toggle-am-pm
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (adj-date-time* s id toggle-am-pm* true)))))

(defmutation set-day
  [{:keys [id day]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (update-day* s id day)))))

(defmutation set-month
  [{:keys [id month]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (update-month* s id month)))))

(defmutation set-hour
  [{:keys [id hour]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (update-hour* s id hour)))))

(defmutation set-minute
  [{:keys [id minute]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (update-minute* s id minute)))))

(defmutation set-mode
  [{:keys [id mode]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (assoc-in s [:date-time-picker id :mode] mode)))))

(defmutation set-time
  [{:keys [id time-str]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (update-if-valid* s id time-str)))))

(defn- weeks-of-interest
  "Modified from the function in https://github.com/untangled-web/untangled-ui/blob/develop/src/main/untangled/ui/calendar.cljc
  Returns a sequence of weeks (each of which contains 7 days) that should be included on a sunday-aligned calendar.
  The weeks are simple lists. The days are javascript Date objects. Their position in the week list indicates their
  day of the week (first position is sunday)."
  [month year]
  (letfn [(prior-day [dt] (tc/minus dt (tc/days 1)))
          (next-day [dt] (tc/plus dt (tc/days 1)))]
    (let [first-day-of-month             (tc/date-time year month 1)
          all-prior-days                 (iterate prior-day first-day-of-month)
          prior-sunday                   (first (drop-while #(not= 7 (tc/day-of-week %)) all-prior-days))
          all-weeks-from-prior-sunday    (partition 7 (iterate next-day prior-sunday))
          contains-this-month?           (fn [week] (some #(= month (tc/month %)) week))
          all-weeks-from-starting-sunday (drop-while (comp not contains-this-month?) all-weeks-from-prior-sunday)]
      (take-while contains-this-month? all-weeks-from-starting-sunday))))

(defn gen-day [dt today display-month cb d]
  (let [month (tc/month d)
        dow (tc/day-of-week d)
        classes (cond-> "day"
                        (< month display-month) (str " old")
                        (> month display-month) (str " new")
                        (>= dow 6) (str " weekend")
                        (tc/equal? d today) (str " today")
                        (tc/equal? (tc/at-midnight dt) (-> d tc/to-default-time-zone tc/at-midnight)) (str " active"))]
    (dom/td #js {:className classes :key (str d) :onClick #(cb d)}
            (dom/div #js {:key (ct/to-string d)} (str (tc/day d))))))

(defsc DateTimePicker [this {:keys [id time month year hour minute open? float-left? no-default? name mode display]}]
  {:initial-state (fn [{:keys [id time float-left name no-default]}]
                    {:id id :time (ct/to-long time) :open? false :float-left? float-left
                     :no-default? no-default :name name :mode :calendar})
   :ident         [:date-time-picker :id]
   :query         [:id :time :day :month :year :hour :minute :open? :float-left? :no-default? :name :mode :display]
   :componentDidMount (fn []
                        (let [check-close (fn [evt] (let [node (dom/node this)
                                                          target (gobj/get evt "target")
                                                          in-widget (.call (gobj/get node "contains") node target)]
                                                      (when-not in-widget
                                                        (m/set-value! this :open? false))))]
                          (gobj/set this "handle-click" check-close)
                          (js/document.addEventListener "click" check-close)))
   :componentWillUnmount (fn [] (js/document.removeEventListener "click" (gobj/get this "handle-click")))}
  (let [classes (cond-> "bootstrap-datetimepicker-widget dropdown-menu bottom"
                        open? (str " open"))
        dt (->> time ct/from-long tc/to-default-time-zone)
        dt-midnight (tc/at-midnight dt)
        today (tc/today-at-midnight)
        display-month (or month (tc/month dt))
        display-year (or year (tc/year dt))
        display-hour (or hour (tc/hour dt))
        display-minute (or minute (tc/minute dt))
        display-str (or display (ft/unparse time-formatter dt))
        two-digit #(cond->> (str %)
                            (< % 10) (str "0"))
        hour-str #(two-digit (if (> % 12) (- % 12) %))
        am-pm #(if (> % 12) "PM" "AM")
        month-year (str (ft/months (dec display-month)) " " display-year)
        prior-month #(prim/transact! this `[(previous-month {:id ~id})])
        next-month #(prim/transact! this `[(next-month {:id ~id})])
        prior-year #(prim/transact! this `[(previous-year {:id ~id})])
        next-year #(prim/transact! this `[(next-year {:id ~id})])
        prior-hour #(prim/transact! this `[(previous-hour {:id ~id})])
        next-hour #(prim/transact! this `[(next-hour {:id ~id})])
        prior-minute #(prim/transact! this `[(previous-minute {:id ~id})])
        next-minute #(prim/transact! this `[(next-minute {:id ~id})])
        tog-am-pm #(prim/transact! this `[(toggle-am-pm {:id ~id})])
        month-mode #(prim/transact! this `[(set-mode {:id ~id :mode :months})])
        time-mode #(prim/transact! this `[(set-mode {:id ~id :mode :time})])
        time-hours-mode #(prim/transact! this `[(set-mode {:id ~id :mode :time-hours})])
        time-minutes-mode #(prim/transact! this `[(set-mode {:id ~id :mode :time-minutes})])
        calendar-mode #(prim/transact! this `[(set-mode {:id ~id :mode :calendar})])
        set-day-cb (fn [d] (prim/transact! this `[(set-day {:id ~id :day ~d})]))
        set-month-cb (fn [m] (prim/transact! this `[(set-month {:id ~id :month ~m})]))
        set-minute-cb (fn [m] (prim/transact! this `[(set-minute {:id ~id :minute ~m})]))
        set-hour-cb (fn [h] (prim/transact! this `[(set-hour {:id ~id :hour ~h})]))
        set-time-str (fn [ts] (prim/transact! this `[(set-time {:id ~id :time-str ~ts})]))
        weeks (weeks-of-interest display-month display-year)
        gen-week (fn [week] (dom/tr #js {:key (str "wk-" (first week))}
                                    (mapv #(gen-day dt-midnight today display-month set-day-cb %) week)))
        calendar (mapv gen-week weeks)
        gen-month (fn [idx month] (dom/span #js {:className (cond-> "month"
                                                                    (and (= idx (dec display-month))
                                                                         (= (tc/year dt) display-year))
                                                                    (str " active"))
                                                 :key       month
                                                 :onClick   #(set-month-cb (inc idx))}
                                            (subs month 0 3)))
        gen-td (fn [seq cls conv cb] (map (fn [n] (dom/td #js {:className cls :key n :onClick #(cb n)}
                                                          (dom/div nil (conv n)))) seq))
        gen-row (fn [seq cls conv cb] (map-indexed (fn [i v] (dom/tr #js {:key i} v))
                                                   (partition 4 (gen-td seq cls conv cb))))
        time-mode? (#{:time :time-hours :time-minutes} mode)
        invalid (try (ft/parse time-formatter display-str) (catch js/Error _ false))
        form-group {:className "form-group"}
        float-left {:float "left"}
        input-props {:type    "text"
                     :placeholder "dd/mm/yyyy HH:MM"
                     :className "form-control"
                     :name (or name "none")
                     :size 16
                     :onClick #(m/toggle! this :open?)
                     :onChange (fn [evt]
                                 (let [new-value (.. evt -target -value)]
                                   (m/set-string! this :display :value new-value)
                                   (set-time-str new-value)))}]
    (dom/div (clj->js (cond-> form-group
                              float-left? (assoc :style float-left)))
             (dom/input (clj->js (assoc input-props :value (if no-default? "" display-str))))
             (when-not (tc/date? invalid)
               (dom/label #js {:className b/text-danger} "Invalid"))
             (dom/div #js {:className classes}
                      (dom/ul #js {:className "list-unstyled"}
                              (when time-mode?
                                (dom/li #js {:className "picker-switch"}
                                        (dom/table #js {:className "table-condensed"}
                                                   (dom/tbody #js {}
                                                              (dom/tr #js {}
                                                                      (dom/td #js {}
                                                                              (dom/a #js {:title "Select Time"}
                                                                                     (dom/span
                                                                                       #js {:className "fa fa-calendar"
                                                                                            :onClick calendar-mode}))))))))
                              (dom/li #js {:className (if open? "collapse in" "collapse")}
                                      (case mode
                                        :calendar (dom/div #js {:className "datepicker"}
                                                           (dom/div #js {:className "datepicker-days"}
                                                                    (dom/table #js {:className "table-condensed"}
                                                                               (dom/thead #js {}
                                                                                          (dom/tr #js {}
                                                                                                  (dom/th #js {:className "prev"}
                                                                                                          (dom/span
                                                                                                            #js {:title     "Previous Month"
                                                                                                                 :className "fa fa-chevron-left"
                                                                                                                 :onClick   prior-month}))
                                                                                                  (dom/th #js {:colSpan   "5" :title "Select Month"
                                                                                                               :className "picker-switch"
                                                                                                               :onClick   month-mode} month-year)
                                                                                                  (dom/th #js {:className "next"}
                                                                                                          (dom/span
                                                                                                            #js {:title     "Next Month"
                                                                                                                 :className "fa fa-chevron-right"
                                                                                                                 :onClick   next-month})))
                                                                                          (dom/tr #js {}
                                                                                                  (dom/th #js {:className "dow"} "Su")
                                                                                                  (dom/th #js {:className "dow"} "Mo")
                                                                                                  (dom/th #js {:className "dow"} "Tu")
                                                                                                  (dom/th #js {:className "dow"} "We")
                                                                                                  (dom/th #js {:className "dow"} "Th")
                                                                                                  (dom/th #js {:className "dow"} "Fr")
                                                                                                  (dom/th #js {:className "dow"} "Sa")))
                                                                               (dom/tbody #js {} calendar))))
                                        :months (dom/div #js {:className "datepicker-months"}
                                                         (dom/table #js {:className "table-condensed"}
                                                                    (dom/thead #js {}
                                                                               (dom/tr #js {}
                                                                                       (dom/th #js {:className "prev"}
                                                                                               (dom/span
                                                                                                 #js {:title     "Previous Year"
                                                                                                      :className "fa fa-chevron-left"
                                                                                                      :onClick   prior-year}))
                                                                                       (dom/th #js {:colSpan   "5"
                                                                                                    :title     "Select Year"
                                                                                                    :className "picker-switch"}
                                                                                               display-year)
                                                                                       (dom/th #js {:className "next"}
                                                                                               (dom/span #js {:title     "Next Year"
                                                                                                              :className "fa fa-chevron-right"
                                                                                                              :onClick   next-year}))))
                                                                    (dom/tbody #js {}
                                                                               (dom/tr #js {} (dom/td #js {:colSpan "7"}
                                                                                                      (map-indexed gen-month ft/months))))))
                                        :time (dom/div #js {:className "timepicker"}
                                                       (dom/div #js {:className "timepicker-picker"}
                                                                (dom/table #js {:className "table-condensed"}
                                                                           (dom/tbody #js {}
                                                                                      (dom/tr #js {}
                                                                                              (dom/td #js {}
                                                                                                      (dom/a #js {:href        "#"
                                                                                                                  :title       "Increment Hour"
                                                                                                                  :className   "btn"
                                                                                                                  :data-action ""
                                                                                                                  :onClick     next-hour
                                                                                                                  :tabIndex    "-1"}
                                                                                                             (dom/span #js {:className "fa fa-chevron-up"})))
                                                                                              (dom/td #js {:className "separator"})
                                                                                              (dom/td #js {}
                                                                                                      (dom/a #js {:href        "#"
                                                                                                                  :title       "Increment Minute"
                                                                                                                  :className   "btn"
                                                                                                                  :data-action ""
                                                                                                                  :onClick     next-minute
                                                                                                                  :tabIndex    "-1"}
                                                                                                             (dom/span #js {:className "fa fa-chevron-up"})))
                                                                                              (dom/td #js {:className "separator"}))
                                                                                      (dom/tr #js {}
                                                                                              (dom/td #js {}
                                                                                                      (dom/span #js {:title     "Pick Hour"
                                                                                                                     :className "timepicker-hour"
                                                                                                                     :onClick   time-hours-mode}
                                                                                                                (hour-str display-hour)))
                                                                                              (dom/td #js {:className "separator"} ":")
                                                                                              (dom/td #js {}
                                                                                                      (dom/span #js {:title     "Pick Minute"
                                                                                                                     :className "timepicker-minute"
                                                                                                                     :onClick   time-minutes-mode}
                                                                                                                (two-digit display-minute)))
                                                                                              (dom/td #js {}
                                                                                                      (dom/button #js {:title     "Toggle Period"
                                                                                                                       :className "btn btn-primary btn-round"
                                                                                                                       :onClick   tog-am-pm
                                                                                                                       :tabIndex  "-1"}
                                                                                                                  (am-pm display-hour))))
                                                                                      (dom/tr #js {}
                                                                                              (dom/td #js {}
                                                                                                      (dom/a #js {:href        "#"
                                                                                                                  :title       "Decrement Hour"
                                                                                                                  :className   "btn"
                                                                                                                  :data-action ""
                                                                                                                  :onClick     prior-hour
                                                                                                                  :tabIndex    "-1"}
                                                                                                             (dom/span #js {:className "fa fa-chevron-down"})))
                                                                                              (dom/td #js {:className "separator"})
                                                                                              (dom/td #js {}
                                                                                                      (dom/a #js {:href        "#"
                                                                                                                  :title       "Decrement Minute"
                                                                                                                  :className   "btn"
                                                                                                                  :data-action ""
                                                                                                                  :onClick     prior-minute
                                                                                                                  :tabIndex    "-1"}
                                                                                                             (dom/span #js {:className "fa fa-chevron-down"})))
                                                                                              (dom/td #js {:className "separator"}))))))
                                        :time-hours (dom/div #js {:className "timepicker-hours"}
                                                             (dom/table #js {:className "table-condensed"}
                                                                        (dom/tbody #js {}
                                                                                   (gen-row (range 12) "hour" two-digit set-hour-cb))))
                                        :time-minutes (dom/div #js {:className "timepicker-minutes"}
                                                               (dom/table #js {:className "table-condensed"}
                                                                          (dom/tbody #js {}
                                                                                     (gen-row (range 0 60 5) "minute" two-digit set-minute-cb))))))
                              (when-not time-mode?
                                (dom/li #js {:className "picker-switch"}
                                        (dom/table #js {:className "table-condensed"}
                                                   (dom/tbody #js {}
                                                              (dom/tr #js {}
                                                                      (dom/td #js {}
                                                                              (dom/a #js {:title "Select Time"}
                                                                                     (dom/span
                                                                                       #js {:className "fa fa-clock-o"
                                                                                            :onClick   time-mode})))))))))))))

(def date-time-picker (prim/factory DateTimePicker {:keyfn :id}))
