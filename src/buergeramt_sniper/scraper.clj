(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [schema.core :as s]
            [clj-time.format :as tf]
            [buergeramt-sniper.loader :refer [Dom]])
  (:import (org.joda.time DateTime)))

(s/defrecord Time
  [time :- s/Str
   place :- s/Str
   href :- s/Str])

(s/defrecord DayPage
  [times :- [Time]])

(s/defrecord OpenDate
  [text :- s/Str
   href :- s/Str
   date :- DateTime])

(s/defrecord ClosedDate
  [text :- s/Str])

(s/defrecord Month
  [name :- s/Str
   closed-dates :- [ClosedDate]
   open-dates :- [OpenDate]
   prev-href :- (s/maybe s/Str)
   next-href :- (s/maybe s/Str)])

(s/defrecord CalendarPage
  [months :- [Month]])

(s/defrecord RootPage
  [appointment-href :- s/Str
   title :- s/Str])

(s/defrecord AppointmentPage
  [hidden-inputs :- {s/Str s/Str}
   form-action :- s/Str])

(s/defn parse-root-page :- RootPage
  [dom :- [Dom]]
  (strict-map->RootPage
    {:appointment-href (-> (html/select dom [:div.zmstermin-multi :a.btn]) first :attrs :href)
     :title            (-> (html/select dom [:div.article :div.header :h1.title]) first html/text str/trim)}))

(s/defn parse-closed-date :- ClosedDate
  [dom :- Dom]
  (strict-map->ClosedDate {:text (-> (html/text dom) str/trim)}))

(s/defn parse-open-date :- OpenDate
  [dom :- Dom]
  (let [href (-> (html/select dom [:a]) first :attrs :href)
        date (->> href
                  (re-seq #"datum=([^&]*)")
                  first second
                  (tf/parse (tf/formatters :date)))]
    (strict-map->OpenDate {:text (-> (html/text dom) str/trim)
                           :href href
                           :date date})))

(s/defn parse-month :- Month
  [dom :- Dom]
  (strict-map->Month
    {:name         (-> (html/select dom [:th.month]) first html/text str/trim)
     :prev-href    (-> (html/select dom [:th.prev :a]) first :attrs :href)
     :next-href    (-> (html/select dom [:th.next :a]) first :attrs :href)
     :closed-dates (->> (html/select dom [:td.nichtbuchbar]) (map parse-closed-date))
     :open-dates   (->> (html/select dom [:td.buchbar]) (map parse-open-date))}))

(s/defn parse-calendar-page :- (s/maybe CalendarPage)
  [dom :- [Dom]]
  (log/debug "Parsing calendar page")
  (when-let [months (-> dom (html/select [:div.calendar-month-table]) seq)]
    (strict-map->CalendarPage {:months (map parse-month months)})))

(s/defn parse-timetable-record :- Time
  [dom :- Dom]
  (let [[th] (html/select dom [:th])
        [td-a] (html/select dom [:td :a])]
    (strict-map->Time {:time  (some-> th html/text str/trim)
                       :place (some-> td-a html/text str/trim)
                       :href  (some-> td-a :attrs :href)})))

(s/defn fix-omitted-times :- [Time]
  "If :time for record is empty, take it from the previous record"
  [records :- [Time]]
  (->>
    (reduce
      (fn [[last-time acc] r]
        (if (str/blank? (:time r))
          [last-time (conj acc (assoc r :time last-time))]
          [(:time r) (conj acc r)]))
      ["" []]
      records)
    second))

(s/defn parse-daytimes-page :- DayPage
  [dom :- [Dom]]
  (log/debug "Parsing daytimes page")
  (strict-map->DayPage {:times (->> (html/select dom [:div.timetable :tr])
                                    (map parse-timetable-record)
                                    (fix-omitted-times))}))

(s/defn parse-hidden-field :- {s/Str s/Str}
  [hidden-input :- Dom]
  (let [{:keys [name value]} (:attrs hidden-input)]
    {name value}))

(s/defn parse-appointment-page :- AppointmentPage
  [dom :- [Dom]]
  (log/debug "Parsing appointment page")
  (let [hidden-inputs (->> (html/select dom [:div#kundendaten :form [:input (html/attr= :type "hidden")]])
                           (map parse-hidden-field)
                           (apply merge))
        form-action (-> (html/select dom [:div#kundendaten :form]) first :attrs :action)]
    (strict-map->AppointmentPage {:hidden-inputs hidden-inputs
                                  :form-action   form-action})))