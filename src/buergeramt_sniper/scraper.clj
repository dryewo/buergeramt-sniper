(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defschema Dom
  "Enlive DOM schema"
  (s/either s/Str
            {:type s/Any
             :data s/Any}
            {:tag     s/Any
             :attrs   s/Any
             :content s/Any}))

(s/defrecord Time
  [time :- s/Str
   place :- s/Str
   href :- s/Str])

(s/defrecord Day
  [times :- [Time]])

(s/defrecord Month
  [name :- s/Str
   closed-dates :- [s/Any]
   open-dates :- [s/Any]
   prev-href :- (s/maybe s/Str)
   next-href :- (s/maybe s/Str)])

(s/defrecord OpenDate
  [text :- s/Str
   href :- s/Str])

(s/defrecord ClosedDate
  [text :- s/Str])

(s/defrecord CalendarPage
  [months :- [Month]])

(s/defrecord RootPage
  [appointment-href :- s/Str
   title :- s/Str])

(s/defn ^:always-val1idate parse-root-page :- RootPage
  [dom :- [Dom]]
  (strict-map->RootPage
    {:appointment-href (-> (html/select dom [:div.zmstermin-multi :a.btn])
                           first :attrs :href)
     :title            (-> (html/select dom [:div.article :div.header :h1.title])
                           first html/text str/trim)}))

(s/defn ^:always-validate parse-closed-date :- ClosedDate
  [dom :- Dom]
  (strict-map->ClosedDate {:text (-> dom html/text str/trim)}))

(s/defn ^:always-validate parse-open-date :- OpenDate
  [dom :- Dom]
  (strict-map->OpenDate {:text (-> dom html/text str/trim)
                         :href (-> dom
                                   (html/select [:a])
                                   first
                                   :attrs
                                   :href)}))

(s/defn ^:always-validate parse-month :- Month
  [dom :- Dom]
  (strict-map->Month {:name         (-> (html/select dom [:th.month])
                                        first html/text str/trim)
                      :prev-href    (-> (html/select dom [:th.prev :a])
                                        first :attrs :href)
                      :next-href    (-> (html/select dom [:th.next :a])
                                        first :attrs :href)
                      :closed-dates (->> (html/select dom [:td.nichtbuchbar])
                                         (map parse-closed-date))
                      :open-dates   (->> (html/select dom [:td.buchbar])
                                         (map parse-open-date))}))

(s/defn ^:always-validate parse-calendar-page :- CalendarPage
  [dom :- [Dom]]
  (log/debug "Parsing calendar page")
  (when-let [months (-> dom (html/select [:div.calendar-month-table]) seq)]
    (strict-map->CalendarPage {:months (map parse-month months)})))

(s/defn ^:always-validate parse-timetable-record :- Time
  [dom :- Dom]
  (let [[th] (html/select dom [:th])
        [td-a] (html/select dom [:td :a])]
    (strict-map->Time {:time  (some-> th html/text str/trim)
                       :place (some-> td-a html/text str/trim)
                       :href  (some-> td-a :attrs :href)})))

(s/defn ^:always-validate fix-omitted-times :- [Time]
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

(s/defn ^:always-validate parse-daytimes-page :- Day
  [dom :- [Dom]]
  (log/debug "Parsing daytimes page")
  (strict-map->Day {:times (->> (html/select dom [:div.timetable :tr])
                                (map parse-timetable-record)
                                (fix-omitted-times))}))
